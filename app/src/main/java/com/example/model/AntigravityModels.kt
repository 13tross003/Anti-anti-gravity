package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AgentMode(val label: String, val subtitle: String) {
    PLANNING("Planning", "Produces Implementation Plan & Task List for review first"),
    FAST("Fast", "Executes code edits and workspace tools directly");

    companion object {
        fun safeParse(raw: String?): AgentMode =
            entries.firstOrNull { it.name == raw } ?: PLANNING
    }
}

enum class SessionStatus(val label: String) {
    IDLE("Idle"),
    RUNNING("Running"),
    NEEDS_REVIEW("Needs Review"),
    COMPLETED("Completed"),
    ERROR("Error");

    companion object {
        fun safeParse(raw: String?): SessionStatus =
            entries.firstOrNull { it.name == raw } ?: IDLE
    }
}

enum class MessageRole {
    USER,
    AGENT,
    TOOL,
    SYSTEM;

    companion object {
        fun safeParse(raw: String?): MessageRole =
            entries.firstOrNull { it.name == raw } ?: AGENT
    }
}

enum class ToolStatus {
    NONE,
    PENDING,
    SUCCESS,
    BLOCKED,
    ERROR;

    companion object {
        fun safeParse(raw: String?): ToolStatus =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}

enum class ArtifactKind(val label: String) {
    PLAN("Implementation Plan"),
    TASK_LIST("Task List"),
    DIFF("Code Diff"),
    WALKTHROUGH("Walkthrough"),
    HTML_PREVIEW("Generative UI");

    companion object {
        fun safeParse(raw: String?): ArtifactKind =
            entries.firstOrNull { it.name == raw } ?: PLAN
    }
}

enum class ApprovalStatus(val label: String) {
    PENDING("Pending Review"),
    APPROVED("Approved"),
    CHANGES_REQUESTED("Changes Requested");

    companion object {
        fun safeParse(raw: String?): ApprovalStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class BuiltinTool(val sdkName: String, val description: String, val readOnlySafe: Boolean) {
    READ_FILE("BuiltinTools.READ_FILE", "Inspect files and symbols in workspace", true),
    WRITE_FILE("BuiltinTools.WRITE_FILE", "Create and modify workspace files", false),
    RUN_COMMAND("BuiltinTools.RUN_COMMAND", "Execute terminal commands & tests", false),
    SEARCH_CODE("BuiltinTools.SEARCH_CODE", "Search codebase definitions & refs", true),
    BROWSER_ACTUATE("BuiltinTools.BROWSER_ACTUATE", "Preview & inspect UI in /browser", true);
}

enum class GeminiModelOption(val modelId: String, val displayName: String, val badge: String) {
    GEMINI_3_8_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "Default"),
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Ultra Fast"),
    GEMINI_4_ARGON("gemini-4-argon", "Gemini 4 Argon", "Deep Reasoning");

    companion object {
        fun safeParse(raw: String?): GeminiModelOption =
            entries.firstOrNull { it.modelId == raw } ?: GEMINI_3_8_FLASH
    }
}

@Keep
@IgnoreExtraProperties
data class AgentProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Developer",
    val preferredModel: String = GeminiModelOption.GEMINI_3_8_FLASH.modelId,
    val agentMode: String = AgentMode.PLANNING.name,
    val readOnlyMode: Boolean = false,
    val enabledTools: List<String> = BuiltinTool.entries.map { it.name },
    val blockDestructiveHooks: Boolean = true,
    val darkTheme: Boolean = true,
    val browserAllowlist: List<String> = listOf("antigravity.google", "localhost", "127.0.0.1", "ai.google.dev"),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Workspace(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val language: String = "Python",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class WorkspaceFile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val path: String = "",
    val language: String = "python",
    val content: String = "",
    val previousContent: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AgentSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val workspaceName: String = "",
    val title: String = "",
    val status: String = SessionStatus.IDLE.name,
    val mode: String = AgentMode.PLANNING.name,
    val modelName: String = GeminiModelOption.GEMINI_3_8_FLASH.modelId,
    val summary: String = "",
    val tasksTotal: Int = 0,
    val tasksCompleted: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AgentMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val role: String = MessageRole.AGENT.name,
    val content: String = "",
    val toolName: String = "",
    val toolStatus: String = ToolStatus.NONE.name,
    val artifactId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class Artifact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val sessionId: String = "",
    val title: String = "",
    val kind: String = ArtifactKind.PLAN.name,
    val content: String = "",
    val approvalStatus: String = ApprovalStatus.PENDING.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
