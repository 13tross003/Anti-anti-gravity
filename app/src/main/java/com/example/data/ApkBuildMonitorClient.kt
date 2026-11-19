package com.example.data

import com.example.model.ApkArtifactInfo
import com.example.model.ApkBuildMonitorState
import com.example.model.ApkBuildPhase
import com.example.model.ApkBuildStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApkBuildMonitorClient {

    private fun nowFormatted(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun httpGet(urlString: String, githubToken: String? = null): Pair<Int, String> {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Google-Antigravity-Android/1.0")
            if (!githubToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer ${githubToken.trim()}")
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            }.orEmpty()
            code to body
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchGitHubBuildStatus(current: ApkBuildMonitorState): ApkBuildMonitorState =
        withContext(Dispatchers.IO) {
            val cleanRepo = current.githubRepo.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .trim('/')
            val parts = cleanRepo.split("/")
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return@withContext current.copy(
                    isRefreshing = false,
                    isPolling = false,
                    phase = ApkBuildPhase.IDLE,
                    errorMessage = "Enter a valid GitHub repository as owner/repo (e.g. octocat/my-android-app).",
                )
            }
            val owner = parts[0]
            val repo = parts[1]
            val token = current.githubToken.takeIf { it.isNotBlank() }

            try {
                val (runsCode, runsBody) = httpGet(
                    "https://api.github.com/repos/$owner/$repo/actions/runs?per_page=1",
                    token
                )
                if (runsCode == 404) {
                    return@withContext current.copy(
                        isRefreshing = false,
                        isPolling = false,
                        phase = ApkBuildPhase.DISCONNECTED,
                        lastCheckedAt = nowFormatted(),
                        errorMessage = "Repository '$cleanRepo' or GitHub Actions not found (404). Add a GitHub token if the repo is private.",
                    )
                }
                if (runsCode == 401 || runsCode == 403) {
                    val apiMsg = runCatching { JSONObject(runsBody).optString("message") }.getOrNull()
                    return@withContext current.copy(
                        isRefreshing = false,
                        isPolling = false,
                        phase = ApkBuildPhase.DISCONNECTED,
                        lastCheckedAt = nowFormatted(),
                        errorMessage = "GitHub API access denied ($runsCode)${if (!apiMsg.isNullOrBlank()) ": $apiMsg" else "."}",
                    )
                }
                if (runsCode !in 200..299) {
                    return@withContext current.copy(
                        isRefreshing = false,
                        phase = ApkBuildPhase.DISCONNECTED,
                        lastCheckedAt = nowFormatted(),
                        errorMessage = "GitHub API returned HTTP $runsCode.",
                    )
                }

                val root = JSONObject(runsBody)
                val runs = root.optJSONArray("workflow_runs") ?: JSONArray()
                if (runs.length() == 0) {
                    return@withContext current.copy(
                        isRefreshing = false,
                        phase = ApkBuildPhase.IDLE,
                        progress = 0f,
                        stageLabel = "No workflow runs found yet on $cleanRepo",
                        steps = emptyList(),
                        artifacts = emptyList(),
                        lastCheckedAt = nowFormatted(),
                        errorMessage = null,
                    )
                }

                val run = runs.getJSONObject(0)
                val runId = run.getLong("id")
                val status = run.optString("status", "queued")
                val conclusion = run.optString("conclusion", "").takeIf { it.isNotBlank() && it != "null" }
                val workflowName = run.optString("name", "Android CI")
                val runNumber = run.optInt("run_number", 0).takeIf { it > 0 }
                val branch = run.optString("head_branch", "")
                val commitSha = run.optString("head_sha", "").take(7)
                val htmlUrl = run.optString("html_url", "")
                val startedAt = run.optString("run_started_at", run.optString("created_at", ""))
                val updatedAt = run.optString("updated_at", "")

                // Fetch real job steps for granular progress
                val steps = mutableListOf<ApkBuildStep>()
                val (jobsCode, jobsBody) = httpGet(
                    "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs",
                    token
                )
                if (jobsCode in 200..299) {
                    val jobs = JSONObject(jobsBody).optJSONArray("jobs") ?: JSONArray()
                    if (jobs.length() > 0) {
                        val stepsArr = jobs.getJSONObject(0).optJSONArray("steps") ?: JSONArray()
                        for (i in 0 until stepsArr.length()) {
                            val s = stepsArr.getJSONObject(i)
                            val c = s.optString("conclusion", "").takeIf { it.isNotBlank() && it != "null" }
                            steps += ApkBuildStep(
                                number = s.optInt("number", i + 1),
                                name = s.optString("name", "Step ${i + 1}"),
                                status = s.optString("status", "queued"),
                                conclusion = c,
                            )
                        }
                    }
                }

                // Fetch real generated APK artifacts
                val artifacts = mutableListOf<ApkArtifactInfo>()
                if (status == "completed") {
                    val (artCode, artBody) = httpGet(
                        "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/artifacts",
                        token
                    )
                    if (artCode in 200..299) {
                        val artArr = JSONObject(artBody).optJSONArray("artifacts") ?: JSONArray()
                        for (i in 0 until artArr.length()) {
                            val a = artArr.getJSONObject(i)
                            artifacts += ApkArtifactInfo(
                                name = a.optString("name", "app-debug-apk"),
                                sizeBytes = a.optLong("size_in_bytes", 0L),
                                downloadUrl = htmlUrl,
                                expired = a.optBoolean("expired", false),
                            )
                        }
                    }
                }

                val phase = when {
                    status == "queued" || status == "waiting" || status == "requested" -> ApkBuildPhase.QUEUED
                    status == "in_progress" -> ApkBuildPhase.COMPILING
                    status == "completed" && conclusion == "success" -> ApkBuildPhase.SUCCEEDED
                    status == "completed" -> ApkBuildPhase.FAILED
                    else -> ApkBuildPhase.COMPILING
                }

                val activeStep = steps.firstOrNull { it.status == "in_progress" }
                val completedSteps = steps.count { it.status == "completed" }
                val progress = when {
                    phase == ApkBuildPhase.SUCCEEDED -> 1f
                    phase == ApkBuildPhase.QUEUED -> 0.05f
                    steps.isNotEmpty() -> (completedSteps.toFloat() / steps.size.toFloat()).coerceIn(0.08f, 0.96f)
                    phase == ApkBuildPhase.COMPILING -> 0.5f
                    else -> 0f
                }

                val stageLabel = when (phase) {
                    ApkBuildPhase.QUEUED -> "Waiting for GitHub Actions runner..."
                    ApkBuildPhase.COMPILING -> activeStep?.let { "Step ${it.number}/${steps.size}: ${it.name}" }
                        ?: "Compiling APK on GitHub runner..."
                    ApkBuildPhase.SUCCEEDED -> if (artifacts.isNotEmpty()) {
                        "Build succeeded • ${artifacts.first().name} ready to download"
                    } else {
                        "Workflow completed successfully"
                    }
                    ApkBuildPhase.FAILED -> "Build failed (${conclusion ?: "error"})"
                    else -> status
                }

                val keepPolling = current.isPolling && (phase == ApkBuildPhase.QUEUED || phase == ApkBuildPhase.COMPILING)

                current.copy(
                    isRefreshing = false,
                    isPolling = keepPolling,
                    phase = phase,
                    progress = progress,
                    stageLabel = stageLabel,
                    workflowName = workflowName,
                    runNumber = runNumber,
                    branch = branch,
                    commitSha = commitSha,
                    runHtmlUrl = htmlUrl,
                    startedAt = startedAt,
                    updatedAt = updatedAt,
                    lastCheckedAt = nowFormatted(),
                    steps = steps,
                    artifacts = artifacts,
                    logTail = "",
                    errorMessage = if (phase == ApkBuildPhase.FAILED) {
                        val failedStep = steps.firstOrNull { it.conclusion == "failure" }
                        failedStep?.let { "Failed at step '${it.name}'. Open run URL to inspect full Gradle logs." }
                            ?: "Workflow ended with conclusion '${conclusion ?: "failure"}'."
                    } else null,
                )
            } catch (e: Exception) {
                current.copy(
                    isRefreshing = false,
                    phase = ApkBuildPhase.DISCONNECTED,
                    lastCheckedAt = nowFormatted(),
                    errorMessage = e.localizedMessage ?: "Network error querying GitHub Actions.",
                )
            }
        }

    suspend fun fetchTermuxLocalStatus(current: ApkBuildMonitorState): ApkBuildMonitorState =
        withContext(Dispatchers.IO) {
            val url = current.localStatusUrl.trim().ifBlank { "http://127.0.0.1:8765/build-status.json" }
            try {
                val (code, body) = httpGet(url)
                if (code !in 200..299) {
                    return@withContext current.copy(
                        isRefreshing = false,
                        phase = ApkBuildPhase.DISCONNECTED,
                        lastCheckedAt = nowFormatted(),
                        errorMessage = "Termux status endpoint returned HTTP $code.",
                    )
                }

                val json = JSONObject(body)
                val rawPhase = json.optString("phase", "compiling").lowercase()
                val phase = when (rawPhase) {
                    "queued" -> ApkBuildPhase.QUEUED
                    "compiling", "in_progress", "running" -> ApkBuildPhase.COMPILING
                    "succeeded", "success", "completed" -> ApkBuildPhase.SUCCEEDED
                    "failed", "error" -> ApkBuildPhase.FAILED
                    else -> ApkBuildPhase.IDLE
                }
                val progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)
                val stageLabel = json.optString("stageLabel", phase.label)
                val updatedAt = json.optString("updatedAt", nowFormatted())
                val apkPath = json.optString("apkPath", "")
                val apkSize = json.optLong("apkSizeBytes", 0L)
                val logTail = json.optString("logTail", "")

                val steps = mutableListOf<ApkBuildStep>()
                val stepsArr = json.optJSONArray("steps") ?: JSONArray()
                for (i in 0 until stepsArr.length()) {
                    val s = stepsArr.getJSONObject(i)
                    steps += ApkBuildStep(
                        number = s.optInt("number", i + 1),
                        name = s.optString("name", "Stage ${i + 1}"),
                        status = s.optString("status", "queued"),
                        conclusion = s.optString("conclusion", "").takeIf { it.isNotBlank() && it != "null" },
                    )
                }

                val artifacts = if (phase == ApkBuildPhase.SUCCEEDED && apkPath.isNotBlank()) {
                    listOf(
                        ApkArtifactInfo(
                            name = apkPath.substringAfterLast('/'),
                            sizeBytes = apkSize,
                            downloadUrl = "",
                        )
                    )
                } else emptyList()

                val keepPolling = current.isPolling && (phase == ApkBuildPhase.QUEUED || phase == ApkBuildPhase.COMPILING)

                current.copy(
                    isRefreshing = false,
                    isPolling = keepPolling,
                    phase = phase,
                    progress = progress,
                    stageLabel = stageLabel,
                    workflowName = "Termux On-Device Gradle",
                    runNumber = null,
                    branch = "local",
                    commitSha = "",
                    runHtmlUrl = "",
                    updatedAt = updatedAt,
                    lastCheckedAt = nowFormatted(),
                    steps = steps,
                    artifacts = artifacts,
                    logTail = logTail,
                    errorMessage = if (phase == ApkBuildPhase.FAILED) {
                        json.optString("error", "Gradle assembleDebug failed in Termux.")
                    } else null,
                )
            } catch (e: Exception) {
                current.copy(
                    isRefreshing = false,
                    phase = ApkBuildPhase.DISCONNECTED,
                    lastCheckedAt = nowFormatted(),
                    errorMessage = "Waiting for Termux status server at $url (${e.localizedMessage ?: "connection refused"}).",
                )
            }
        }
}
