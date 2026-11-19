package com.example.model

enum class ApkBuildSource(val label: String) {
    GITHUB_ACTIONS("GitHub Actions CI"),
    TERMUX_LOCAL("Termux Localhost"),
}

enum class ApkBuildPhase(val label: String) {
    IDLE("Idle"),
    QUEUED("Queued"),
    COMPILING("Compiling APK"),
    SUCCEEDED("APK Ready"),
    FAILED("Build Failed"),
    DISCONNECTED("Disconnected"),
}

data class ApkBuildStep(
    val number: Int,
    val name: String,
    val status: String,
    val conclusion: String? = null,
)

data class ApkArtifactInfo(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val expired: Boolean = false,
)

data class ApkBuildMonitorState(
    val source: ApkBuildSource = ApkBuildSource.GITHUB_ACTIONS,
    val githubRepo: String = "",
    val githubToken: String = "",
    val localStatusUrl: String = "http://127.0.0.1:8765/build-status.json",
    val isPolling: Boolean = false,
    val isRefreshing: Boolean = false,
    val phase: ApkBuildPhase = ApkBuildPhase.IDLE,
    val progress: Float = 0f,
    val stageLabel: String = "Configure a GitHub repo or start Termux local build telemetry",
    val workflowName: String = "",
    val runNumber: Int? = null,
    val branch: String = "",
    val commitSha: String = "",
    val runHtmlUrl: String = "",
    val startedAt: String = "",
    val updatedAt: String = "",
    val lastCheckedAt: String = "",
    val steps: List<ApkBuildStep> = emptyList(),
    val artifacts: List<ApkArtifactInfo> = emptyList(),
    val logTail: String = "",
    val errorMessage: String? = null,
)
