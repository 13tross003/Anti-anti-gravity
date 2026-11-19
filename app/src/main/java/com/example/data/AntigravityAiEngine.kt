package com.example.data

import android.util.Log
import com.example.model.AgentMode
import com.example.model.AgentProfile
import com.example.model.ArtifactKind
import com.example.model.GeminiModelOption
import com.example.model.WorkspaceFile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AntigravityAiEngine"

data class PlannedFileMutation(
    val path: String,
    val language: String,
    val content: String,
)

data class StructuredAgentAction(
    val summary: String,
    val replyMarkdown: String,
    val toolLogs: List<String>,
    val artifactKind: ArtifactKind,
    val artifactTitle: String,
    val artifactContent: String,
    val fileMutations: List<PlannedFileMutation>,
    val tasksTotal: Int,
    val tasksCompleted: Int,
)

class AntigravityAiEngine {

    private val defaultModel by lazy {
        Firebase.ai.generativeModel(GeminiModelOption.GEMINI_3_8_FLASH.modelId)
    }

    private val liteModel by lazy {
        Firebase.ai.generativeModel(GeminiModelOption.GEMINI_3_5_FLASH_LITE.modelId)
    }

    private val argonModel by lazy {
        Firebase.ai.generativeModel(GeminiModelOption.GEMINI_4_ARGON.modelId)
    }

    private val fileChangeSchema = Schema.obj(
        properties = mapOf(
            "path" to Schema.string("Relative file path e.g. agent_quickstart.py or preview.html"),
            "language" to Schema.string("Language identifier like python, markdown, html, kotlin, javascript"),
            "content" to Schema.string("Complete updated or newly created file source code"),
        )
    )

    private val actionSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string("Short 1-sentence status summary for Agent Manager card"),
            "replyMarkdown" to Schema.string("Detailed, friendly response explaining what was planned or executed"),
            "toolLogs" to Schema.array(Schema.string(), "List of tool call descriptions executed e.g. 'READ_FILE agent_quickstart.py'"),
            "artifactKind" to Schema.enumeration(
                values = listOf("PLAN", "TASK_LIST", "WALKTHROUGH", "HTML_PREVIEW"),
                description = "Primary artifact type produced"
            ),
            "artifactTitle" to Schema.string("Concise title for the produced artifact"),
            "artifactContent" to Schema.string("Complete Markdown or HTML content for the artifact"),
            "fileChanges" to Schema.array(
                items = fileChangeSchema,
                description = "Zero or more workspace files to create or update when executing"
            ),
            "tasksTotal" to Schema.integer("Total number of checklist steps"),
            "tasksCompleted" to Schema.integer("Completed checklist steps so far"),
        ),
        optionalProperties = listOf("fileChanges")
    )

    private val structuredConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = actionSchema
    }

    private val structuredFlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiModelOption.GEMINI_3_8_FLASH.modelId,
            generationConfig = structuredConfig
        )
    }

    private val structuredLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiModelOption.GEMINI_3_5_FLASH_LITE.modelId,
            generationConfig = structuredConfig
        )
    }

    private val structuredArgonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = GeminiModelOption.GEMINI_4_ARGON.modelId,
            generationConfig = structuredConfig
        )
    }

    private fun pickModel(modelId: String) = when (modelId) {
        GeminiModelOption.GEMINI_3_5_FLASH_LITE.modelId -> liteModel
        GeminiModelOption.GEMINI_4_ARGON.modelId -> argonModel
        else -> defaultModel
    }

    private fun pickStructuredModel(modelId: String) = when (modelId) {
        GeminiModelOption.GEMINI_3_5_FLASH_LITE.modelId -> structuredLiteModel
        GeminiModelOption.GEMINI_4_ARGON.modelId -> structuredArgonModel
        else -> structuredFlashModel
    }

    fun streamInlineExplanation(
        modelId: String,
        file: WorkspaceFile,
        instruction: String,
    ): Flow<String> = flow {
        val model = pickModel(modelId)
        val prompt = """
You are the Google Antigravity IDE inline code assistant.
Active file: `${file.path}` (${file.language})
```${file.language}
${file.content}
```
Developer request: $instruction

Respond clearly and concisely so both beginners and experienced developers can understand and apply it immediately.
""".trimIndent()
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    suspend fun generateInlineFileEdit(
        modelId: String,
        file: WorkspaceFile,
        instruction: String,
    ): Result<String> = try {
        val model = pickModel(modelId)
        val prompt = """
You are Google Antigravity's inline code editor.
Rewrite the entire file `${file.path}` (${file.language}) to fulfill the user's instruction.
Return ONLY the complete updated file source code with NO markdown code fences and NO commentary.

Current file content:
${file.content}

Instruction: $instruction
""".trimIndent()
        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        val raw = response.text?.trim().orEmpty()
        if (raw.isBlank()) {
            Result.failure(IllegalStateException("Gemini returned an empty file edit."))
        } else {
            val cleaned = raw
                .removePrefix("```${file.language}")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            Result.success(cleaned)
        }
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Inline file edit timed out", e)
        Result.failure(IllegalStateException("Inline edit timed out after 60 seconds. Please try again."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Inline file edit failed", e)
        Result.failure(e)
    }

    suspend fun runAgentTurn(
        profile: AgentProfile,
        workspaceName: String,
        files: List<WorkspaceFile>,
        historySummary: String,
        userPrompt: String,
        forceExecutePhase: Boolean = false,
    ): Result<StructuredAgentAction> = try {
        val effectiveMode = if (forceExecutePhase) AgentMode.FAST else AgentMode.safeParse(profile.agentMode)
        val canWrite = !profile.readOnlyMode && profile.enabledTools.contains("WRITE_FILE")
        val filesContext = files.joinToString("\n\n") { f ->
            "--- FILE: ${f.path} (${f.language}) ---\n${f.content.take(8000)}"
        }

        val systemInstruction = """
You are Google Antigravity, an autonomous agent-first software engineering platform designed to be effortless for anyone to use.
Active Workspace: $workspaceName
Runtime Mode: ${effectiveMode.name} (${effectiveMode.subtitle})
Read-Only Mode: ${profile.readOnlyMode}
Enabled BuiltinTools: ${profile.enabledTools.joinToString(", ")}
Pre-turn Safety Hook Active: ${profile.blockDestructiveHooks}

Rules:
- If Runtime Mode is PLANNING and forceExecute is false: leave fileChanges empty, produce a clear Implementation Plan (artifactKind="PLAN") with markdown checklist items, and invite the user to tap Approve & Execute.
- If Runtime Mode is FAST or forceExecute is true, AND Read-Only Mode is false and WRITE_FILE is enabled: populate fileChanges with one or more complete files to create or update, log tool steps (READ_FILE, WRITE_FILE, RUN_COMMAND), and produce a WALKTHROUGH or HTML_PREVIEW artifact (if building UI/web preview).
- If Read-Only Mode is true or WRITE_FILE is disabled: leave fileChanges empty and perform read-only analysis.

Workspace Files:
$filesContext

Recent Session Context:
$historySummary

User Prompt:
$userPrompt
""".trimIndent()

        val model = pickStructuredModel(profile.preferredModel)
        val response = withTimeout(60_000L) {
            model.generateContent(content { text(systemInstruction) })
        }
        val jsonText = response.text
            ?: return Result.failure(IllegalStateException("Antigravity agent returned an empty response."))

        val json = JSONObject(jsonText)
        val kind = ArtifactKind.safeParse(json.optString("artifactKind", "PLAN"))

        val mutations = mutableListOf<PlannedFileMutation>()
        if (canWrite) {
            val arr = json.optJSONArray("fileChanges")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val p = obj.optString("path", "").trim()
                    val lang = obj.optString("language", "python").trim().ifBlank { "python" }
                    val c = obj.optString("content", "")
                    if (p.isNotBlank() && c.isNotBlank()) {
                        mutations.add(PlannedFileMutation(p, lang, c))
                    }
                }
            }
        }

        val logsArr = json.optJSONArray("toolLogs")
        val logs = if (logsArr != null) {
            (0 until logsArr.length()).mapNotNull { logsArr.optString(it).takeIf(String::isNotBlank) }
        } else emptyList()

        Result.success(
            StructuredAgentAction(
                summary = json.optString("summary", "Completed agent turn").ifBlank { "Completed agent turn" },
                replyMarkdown = json.optString("replyMarkdown", "Task processed."),
                toolLogs = logs,
                artifactKind = kind,
                artifactTitle = json.optString("artifactTitle", kind.label).ifBlank { kind.label },
                artifactContent = json.optString("artifactContent", ""),
                fileMutations = mutations,
                tasksTotal = json.optInt("tasksTotal", 3).coerceIn(1, 20),
                tasksCompleted = json.optInt("tasksCompleted", if (effectiveMode == AgentMode.FAST) 3 else 1).coerceIn(0, 20),
            )
        )
    } catch (e: TimeoutCancellationException) {
        Log.e(TAG, "Agent turn timed out", e)
        Result.failure(IllegalStateException("Agent execution timed out after 60 seconds. Please retry."))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Agent turn failed", e)
        Result.failure(e)
    }
}
