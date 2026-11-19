package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AntigravityAiEngine
import com.example.data.AntigravityRepository
import com.example.data.AntigravitySeedData
import com.example.data.ApkBuildMonitorClient
import com.example.data.TerminalLine
import com.example.data.TerminalShellEngine
import com.example.data.TerminalStream
import com.example.data.TerminalTargetMode
import com.example.data.TerminalUiState
import com.example.model.AgentMessage
import com.example.model.AgentMode
import com.example.model.AgentProfile
import com.example.model.AgentSession
import com.example.model.ApkBuildMonitorState
import com.example.model.ApkBuildPhase
import com.example.model.ApkBuildSource
import com.example.model.ApprovalStatus
import com.example.model.Artifact
import com.example.model.ArtifactKind
import com.example.model.BuiltinTool
import com.example.model.MessageRole
import com.example.model.SessionStatus
import com.example.model.ToolStatus
import com.example.model.UiState
import com.example.model.Workspace
import com.example.model.WorkspaceFile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "AntigravityVM"
private const val STOP_TIMEOUT_MS = 5000L

data class TerminalEntry(
    val id: String,
    val command: String,
    val output: String,
    val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AntigravityViewModel(
    private val repository: AntigravityRepository,
    private val aiEngine: AntigravityAiEngine,
    private val currentUserId: String,
    private val displayName: String,
    private val workspaceRootDir: File = File(System.getProperty("java.io.tmpdir") ?: ".", "antigravity_workspaces"),
) : ViewModel() {

    private fun <T> Flow<T>.toUiState(defaultError: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, defaultError, root)
                emit(UiState.Error(root.message ?: defaultError))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val profileState: StateFlow<UiState<AgentProfile?>> =
        repository.observeProfile(currentUserId).toUiState("Failed to load AgentProfile")

    val workspacesState: StateFlow<UiState<List<Workspace>>> =
        repository.observeWorkspaces(currentUserId).toUiState("Failed to load workspaces")

    val sessionsState: StateFlow<UiState<List<AgentSession>>> =
        repository.observeSessions(currentUserId).toUiState("Failed to load agent sessions")

    val artifactsState: StateFlow<UiState<List<Artifact>>> =
        repository.observeArtifacts(currentUserId).toUiState("Failed to load artifacts")

    private val _selectedWorkspaceId = MutableStateFlow(AntigravitySeedData.DEFAULT_WORKSPACE_ID)
    val selectedWorkspaceId: StateFlow<String> = _selectedWorkspaceId.asStateFlow()

    val filesState: StateFlow<UiState<List<WorkspaceFile>>> =
        _selectedWorkspaceId.flatMapLatest { wsId ->
            if (wsId.isBlank()) flowOf(emptyList())
            else repository.observeFiles(currentUserId, wsId)
        }.toUiState("Failed to load workspace files")

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    val messagesState: StateFlow<UiState<List<AgentMessage>>> =
        _selectedSessionId.flatMapLatest { sessId ->
            if (sessId.isNullOrBlank()) flowOf(emptyList())
            else repository.observeMessages(currentUserId, sessId)
        }.toUiState("Failed to load conversation messages")

    private val _selectedFileId = MutableStateFlow<String?>(null)
    val selectedFileId: StateFlow<String?> = _selectedFileId.asStateFlow()

    private val _selectedArtifactId = MutableStateFlow<String?>(null)
    val selectedArtifactId: StateFlow<String?> = _selectedArtifactId.asStateFlow()

    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _inlineAiResponse = MutableStateFlow<String?>(null)
    val inlineAiResponse: StateFlow<String?> = _inlineAiResponse.asStateFlow()

    private val _isInlineBusy = MutableStateFlow(false)
    val isInlineBusy: StateFlow<Boolean> = _isInlineBusy.asStateFlow()

    private val _bannerError = MutableStateFlow<String?>(null)
    val bannerError: StateFlow<String?> = _bannerError.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>(null)
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    private val _browserHtmlPreview = MutableStateFlow<String?>(null)
    val browserHtmlPreview: StateFlow<String?> = _browserHtmlPreview.asStateFlow()

    private val _browserUrl = MutableStateFlow("https://antigravity.google")
    val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

    private val _browserLogs = MutableStateFlow(
        listOf(
            "[/browser] Browser Subagent initialized with allowlist policy.",
            "[DOM] Ready for autonomous inspection and HTML artifact rendering."
        )
    )
    val browserLogs: StateFlow<List<String>> = _browserLogs.asStateFlow()

    private val _terminalEntries = MutableStateFlow(
        listOf(
            TerminalEntry(
                id = "boot_1",
                command = "antigravity --version",
                output = "Google Antigravity Runtime v1.0.4 (Android Shell + Termux Bridge)\ngoogle-antigravity SDK ready | App Check verified"
            )
        )
    )
    val terminalEntries: StateFlow<List<TerminalEntry>> = _terminalEntries.asStateFlow()

    private val _terminalUiState = MutableStateFlow(TerminalUiState())
    val terminalUiState: StateFlow<TerminalUiState> = _terminalUiState.asStateFlow()
    private var terminalExecJob: Job? = null
    private var terminalLineSeq: Long = 0L

    private val buildMonitorClient = ApkBuildMonitorClient()
    private val _apkBuildState = MutableStateFlow(ApkBuildMonitorState())
    val apkBuildState: StateFlow<ApkBuildMonitorState> = _apkBuildState.asStateFlow()
    private var buildPollingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureSeededForUser(displayName).onFailure { e ->
                _bannerError.value = e.localizedMessage ?: "Failed to initialize workspace."
            }
        }
    }

    fun clearNotices() {
        _bannerError.value = null
        _statusNotice.value = null
    }

    fun selectWorkspace(workspaceId: String) {
        _selectedWorkspaceId.value = workspaceId
        _selectedFileId.value = null
    }

    fun selectFile(fileId: String?) {
        _selectedFileId.value = fileId
        _inlineAiResponse.value = null
    }

    fun openSession(sessionId: String?) {
        _selectedSessionId.value = sessionId
    }

    fun selectArtifact(artifactId: String?) {
        _selectedArtifactId.value = artifactId
    }

    fun openHtmlInBrowser(title: String, htmlContent: String) {
        _browserHtmlPreview.value = htmlContent
        _browserLogs.value = (_browserLogs.value + "[/browser] Loaded artifact preview: $title").takeLast(40)
    }

    fun navigateBrowserUrl(url: String, allowlist: List<String>) {
        val raw = url.trim()
        val isLocalNoScheme = !raw.startsWith("http://") && !raw.startsWith("https://") &&
            (raw.startsWith("localhost", ignoreCase = true) || raw.startsWith("127.0.0.1"))
        val clean = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            isLocalNoScheme -> "http://$raw"
            else -> "https://$raw"
        }
        val hostWithPort = clean.removePrefix("https://").removePrefix("http://").substringBefore("/").lowercase()
        val host = hostWithPort.substringBefore(":")
        val allowed = allowlist.any { domain ->
            val d = domain.lowercase()
            host == d || host.endsWith(".$d")
        } || host == "localhost" || host == "127.0.0.1"
        if (!allowed) {
            _browserLogs.value = (_browserLogs.value + "[BLOCKED] Actuation rule denied '$host'. Add domain in LocalAgentConfig allowlist.").takeLast(40)
            _bannerError.value = "Browser Actuation Rule blocked '$host'. Add it to allowlist in SDK Config."
            return
        }
        _browserHtmlPreview.value = null
        _browserUrl.value = clean
        _browserLogs.value = (_browserLogs.value + "[NAVIGATE] $clean").takeLast(40)
    }

    fun appendBrowserLog(line: String) {
        _browserLogs.value = (_browserLogs.value + line).takeLast(40)
    }

    fun updateProfileConfig(
        preferredModel: String? = null,
        agentMode: AgentMode? = null,
        readOnlyMode: Boolean? = null,
        enabledTools: List<String>? = null,
        blockDestructiveHooks: Boolean? = null,
        darkTheme: Boolean? = null,
        browserAllowlist: List<String>? = null,
    ) {
        viewModelScope.launch {
            repository.updateProfile(
                preferredModel = preferredModel,
                agentMode = agentMode,
                readOnlyMode = readOnlyMode,
                enabledTools = enabledTools,
                blockDestructiveHooks = blockDestructiveHooks,
                darkTheme = darkTheme,
                browserAllowlist = browserAllowlist,
            ).onFailure { e ->
                _bannerError.value = e.localizedMessage ?: "Failed to update LocalAgentConfig."
            }
        }
    }

    fun toggleTool(tool: BuiltinTool, currentTools: List<String>) {
        val next = if (currentTools.contains(tool.name)) {
            currentTools.filterNot { it == tool.name }
        } else {
            currentTools + tool.name
        }
        updateProfileConfig(enabledTools = next)
    }

    fun createWorkspace(name: String, description: String, language: String) {
        viewModelScope.launch {
            repository.createWorkspace(name, description, language)
                .onSuccess { wsId ->
                    _selectedWorkspaceId.value = wsId
                    _selectedFileId.value = null
                    _statusNotice.value = "Created workspace '$name'"
                }
                .onFailure { e ->
                    _bannerError.value = e.localizedMessage ?: "Could not create workspace."
                }
        }
    }

    fun saveWorkspaceFile(
        workspaceId: String,
        existingFile: WorkspaceFile?,
        path: String,
        language: String,
        newContent: String,
    ) {
        viewModelScope.launch {
            val prev = existingFile?.content ?: ""
            repository.upsertFile(
                workspaceId = workspaceId,
                existingFileId = existingFile?.id,
                filePath = path,
                language = language,
                newContent = newContent,
                previousContent = prev,
            ).onSuccess { fileId ->
                _selectedFileId.value = fileId
                if (existingFile != null && prev != newContent) {
                    val diffText = AntigravitySeedData.buildUnifiedDiff(prev, newContent, path)
                    repository.createArtifact(
                        workspaceId = workspaceId,
                        sessionId = _selectedSessionId.value ?: AntigravitySeedData.DEFAULT_SESSION_ID,
                        title = "Diff: $path",
                        kind = ArtifactKind.DIFF,
                        content = diffText,
                        approvalStatus = ApprovalStatus.APPROVED,
                    )
                }
                _statusNotice.value = "Saved $path"
            }.onFailure { e ->
                _bannerError.value = e.localizedMessage ?: "Failed to save file."
            }
        }
    }

    fun deleteWorkspaceFile(workspaceId: String, fileId: String) {
        viewModelScope.launch {
            repository.deleteFile(workspaceId, fileId)
                .onSuccess {
                    if (_selectedFileId.value == fileId) _selectedFileId.value = null
                    _statusNotice.value = "File deleted"
                }
                .onFailure { e ->
                    _bannerError.value = e.localizedMessage ?: "Failed to delete file."
                }
        }
    }

    fun runInlineAsk(file: WorkspaceFile, instruction: String, modelId: String) {
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _isInlineBusy.value = true
            _inlineAiResponse.value = ""
            try {
                aiEngine.streamInlineExplanation(modelId, file, instruction).collect { partial ->
                    _inlineAiResponse.value = partial
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Inline ask failed", e)
                _bannerError.value = e.localizedMessage ?: "Inline assistant failed."
            } finally {
                _isInlineBusy.value = false
            }
        }
    }

    fun applyInlineEdit(workspaceId: String, file: WorkspaceFile, instruction: String, modelId: String) {
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _isInlineBusy.value = true
            try {
                val result = aiEngine.generateInlineFileEdit(modelId, file, instruction)
                result.onSuccess { updatedCode ->
                    repository.upsertFile(
                        workspaceId = workspaceId,
                        existingFileId = file.id,
                        filePath = file.path,
                        language = file.language,
                        newContent = updatedCode,
                        previousContent = file.content,
                    )
                    val diff = AntigravitySeedData.buildUnifiedDiff(file.content, updatedCode, file.path)
                    repository.createArtifact(
                        workspaceId = workspaceId,
                        sessionId = _selectedSessionId.value ?: AntigravitySeedData.DEFAULT_SESSION_ID,
                        title = "Inline Edit Diff: ${file.path}",
                        kind = ArtifactKind.DIFF,
                        content = diff,
                        approvalStatus = ApprovalStatus.PENDING,
                    )
                    _inlineAiResponse.value = "Applied inline edit to `${file.path}` and generated a Code Diff artifact for review."
                    _statusNotice.value = "Updated ${file.path}"
                }.onFailure { e ->
                    _bannerError.value = e.localizedMessage ?: "Inline edit failed."
                }
            } finally {
                _isInlineBusy.value = false
            }
        }
    }

    fun dispatchNewSession(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return
        val profile = (profileState.value as? UiState.Success)?.data ?: AgentProfile(userId = currentUserId)
        val workspaces = (workspacesState.value as? UiState.Success)?.data.orEmpty()
        val activeWs = workspaces.firstOrNull { it.id == _selectedWorkspaceId.value } ?: workspaces.firstOrNull()
        val wsId = activeWs?.id ?: AntigravitySeedData.DEFAULT_WORKSPACE_ID
        val wsName = activeWs?.name ?: "antigravity-sdk-starter"
        val mode = AgentMode.safeParse(profile.agentMode)

        viewModelScope.launch {
            val title = cleanPrompt.take(72)
            val created = repository.createSession(
                workspaceId = wsId,
                workspaceName = wsName,
                title = title,
                mode = mode,
                modelName = profile.preferredModel,
            )
            created.onSuccess { sessionId ->
                _selectedSessionId.value = sessionId
                executeTurnInternal(
                    sessionId = sessionId,
                    workspaceId = wsId,
                    workspaceName = wsName,
                    profile = profile,
                    prompt = cleanPrompt,
                    forceExecutePhase = false,
                )
            }.onFailure { e ->
                _bannerError.value = e.localizedMessage ?: "Failed to create agent session."
            }
        }
    }

    fun sendSessionMessage(session: AgentSession, prompt: String, forceExecute: Boolean = false) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return
        val profile = (profileState.value as? UiState.Success)?.data ?: AgentProfile(userId = currentUserId)
        viewModelScope.launch {
            executeTurnInternal(
                sessionId = session.id,
                workspaceId = session.workspaceId,
                workspaceName = session.workspaceName,
                profile = profile,
                prompt = cleanPrompt,
                forceExecutePhase = forceExecute,
            )
        }
    }

    private suspend fun executeTurnInternal(
        sessionId: String,
        workspaceId: String,
        workspaceName: String,
        profile: AgentProfile,
        prompt: String,
        forceExecutePhase: Boolean,
    ) {
        _isAgentRunning.value = true
        try {
            repository.addMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = prompt,
            )

            // Enforce @hooks.pre_turn safety policy
            val lowered = prompt.lowercase()
            if (profile.blockDestructiveHooks && (lowered.contains("rm -rf") || lowered.contains("drop database"))) {
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.TOOL,
                    content = "HookResult(allow=False, reason=\"Destructive commands are restricted by @hooks.pre_turn.\")",
                    toolName = "@hooks.pre_turn",
                    toolStatus = ToolStatus.BLOCKED,
                )
                repository.updateSession(
                    sessionId = sessionId,
                    status = SessionStatus.ERROR,
                    summary = "Blocked by @hooks.pre_turn safety policy.",
                )
                return
            }

            repository.updateSession(
                sessionId = sessionId,
                status = SessionStatus.RUNNING,
                summary = "Agent reasoning with ${profile.preferredModel}...",
            )

            val files = (filesState.value as? UiState.Success)?.data.orEmpty()
            val messages = (messagesState.value as? UiState.Success)?.data.orEmpty()
            val historySummary = messages.takeLast(6).joinToString("\n") { "${it.role}: ${it.content.take(300)}" }

            val aiResult = aiEngine.runAgentTurn(
                profile = profile,
                workspaceName = workspaceName,
                files = files,
                historySummary = historySummary,
                userPrompt = prompt,
                forceExecutePhase = forceExecutePhase,
            )

            aiResult.onSuccess { action ->
                var createdArtifactId = ""

                // Apply all workspace file mutations if produced and permitted
                action.fileMutations.forEach { mutation ->
                    val existing = files.firstOrNull { it.path.equals(mutation.path, ignoreCase = true) }
                    val prev = existing?.content ?: ""
                    repository.upsertFile(
                        workspaceId = workspaceId,
                        existingFileId = existing?.id,
                        filePath = mutation.path,
                        language = mutation.language,
                        newContent = mutation.content,
                        previousContent = prev,
                    )
                    val diffText = AntigravitySeedData.buildUnifiedDiff(prev, mutation.content, mutation.path)
                    val diffRes = repository.createArtifact(
                        workspaceId = workspaceId,
                        sessionId = sessionId,
                        title = "Diff: ${mutation.path}",
                        kind = ArtifactKind.DIFF,
                        content = diffText,
                        approvalStatus = ApprovalStatus.PENDING,
                    )
                    diffRes.onSuccess { if (createdArtifactId.isBlank()) createdArtifactId = it }
                }

                if (action.artifactContent.isNotBlank()) {
                    val artRes = repository.createArtifact(
                        workspaceId = workspaceId,
                        sessionId = sessionId,
                        title = action.artifactTitle,
                        kind = action.artifactKind,
                        content = action.artifactContent,
                        approvalStatus = if (action.artifactKind == ArtifactKind.WALKTHROUGH) ApprovalStatus.APPROVED else ApprovalStatus.PENDING,
                    )
                    artRes.onSuccess { id ->
                        if (createdArtifactId.isBlank()) createdArtifactId = id
                    }
                }

                action.toolLogs.forEach { logLine ->
                    repository.addMessage(
                        sessionId = sessionId,
                        role = MessageRole.TOOL,
                        content = logLine,
                        toolName = logLine.substringBefore(" ").take(24),
                        toolStatus = ToolStatus.SUCCESS,
                        artifactId = createdArtifactId,
                    )
                }

                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.AGENT,
                    content = action.replyMarkdown,
                    artifactId = createdArtifactId,
                )

                val mode = if (forceExecutePhase) AgentMode.FAST else AgentMode.safeParse(profile.agentMode)
                val finalStatus = if (mode == AgentMode.PLANNING && action.fileMutations.isEmpty()) {
                    SessionStatus.NEEDS_REVIEW
                } else {
                    SessionStatus.COMPLETED
                }

                repository.updateSession(
                    sessionId = sessionId,
                    status = finalStatus,
                    summary = action.summary,
                    tasksTotal = action.tasksTotal,
                    tasksCompleted = action.tasksCompleted,
                    mode = mode,
                )
            }.onFailure { e ->
                repository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.SYSTEM,
                    content = e.localizedMessage ?: "Agent execution error.",
                    toolStatus = ToolStatus.ERROR,
                )
                repository.updateSession(
                    sessionId = sessionId,
                    status = SessionStatus.ERROR,
                    summary = e.localizedMessage ?: "Error during execution",
                )
                _bannerError.value = e.localizedMessage ?: "Agent execution failed."
            }
        } finally {
            _isAgentRunning.value = false
        }
    }

    fun sendBrowserContextToAgent(currentUrl: String, htmlPreview: String?, logs: List<String>) {
        val recentLogs = logs.takeLast(8).joinToString("\n")
        val targetInfo = if (htmlPreview != null) {
            "Inspect the active /browser Generative UI HTML artifact and recent console logs:\n\n$recentLogs\n\nHTML snippet:\n${htmlPreview.take(2000)}"
        } else {
            "Inspect the active /browser page ($currentUrl) and console logs:\n\n$recentLogs"
        }
        dispatchNewSession(targetInfo)
    }

    fun setArtifactApproval(artifact: Artifact, status: ApprovalStatus, feedbackNote: String = "") {
        viewModelScope.launch {
            repository.updateArtifactApproval(artifact.id, status)
                .onSuccess {
                    _statusNotice.value = "Artifact marked ${status.label}"
                    if (feedbackNote.isNotBlank()) {
                        repository.addMessage(
                            sessionId = artifact.sessionId,
                            role = MessageRole.USER,
                            content = "Review feedback on ${artifact.title}: $feedbackNote",
                            artifactId = artifact.id,
                        )
                    }
                }
                .onFailure { e ->
                    _bannerError.value = e.localizedMessage ?: "Failed to update artifact status."
                }
        }
    }

    private fun appendTerminalLine(text: String, stream: TerminalStream) {
        val id = "tl_${++terminalLineSeq}_${System.currentTimeMillis()}"
        val current = _terminalUiState.value
        _terminalUiState.value = current.copy(
            lines = (current.lines + TerminalLine(id = id, text = text, stream = stream)).takeLast(400)
        )
    }

    fun setTerminalMode(mode: TerminalTargetMode) {
        _terminalUiState.value = _terminalUiState.value.copy(mode = mode)
    }

    fun updateTermuxBridgeUrl(url: String) {
        _terminalUiState.value = _terminalUiState.value.copy(termuxBridgeUrl = url)
    }

    fun clearTerminalOutput() {
        _terminalEntries.value = emptyList()
        _terminalUiState.value = _terminalUiState.value.copy(
            lines = emptyList(),
            lastExitCode = null,
        )
    }

    fun stopTerminalExecution() {
        terminalExecJob?.cancel()
        terminalExecJob = null
        appendTerminalLine("^C Process interrupted by user.", TerminalStream.STDERR)
        _terminalUiState.value = _terminalUiState.value.copy(isRunning = false, lastExitCode = 130)
    }

    fun testTermuxBridgeConnection() {
        viewModelScope.launch {
            val state = _terminalUiState.value
            appendTerminalLine("Checking Termux Live Bridge at ${state.termuxBridgeUrl}...", TerminalStream.SYSTEM)
            val (ok, msg) = TerminalShellEngine.pingTermuxBridge(
                bridgeUrl = state.termuxBridgeUrl,
                token = state.termuxBridgeToken,
            )
            _terminalUiState.value = _terminalUiState.value.copy(termuxConnected = ok)
            appendTerminalLine(msg, if (ok) TerminalStream.SYSTEM else TerminalStream.STDERR)
        }
    }

    fun syncTerminalWorkspace() {
        val files = (filesState.value as? UiState.Success)?.data.orEmpty()
        val wsId = _selectedWorkspaceId.value
        val state = _terminalUiState.value
        viewModelScope.launch {
            if (state.mode == TerminalTargetMode.TERMUX_BRIDGE) {
                appendTerminalLine("Syncing ${files.size} workspace files to Termux Live Bridge...", TerminalStream.SYSTEM)
                val res = TerminalShellEngine.syncFilesToTermuxBridge(
                    bridgeUrl = state.termuxBridgeUrl,
                    token = state.termuxBridgeToken,
                    workspaceId = wsId,
                    files = files,
                )
                res.outputLines.forEach { (line, stream) -> appendTerminalLine(line, stream) }
                _terminalUiState.value = _terminalUiState.value.copy(
                    termuxConnected = res.exitCode == 0,
                    termuxCwd = res.termuxCwd ?: _terminalUiState.value.termuxCwd,
                    lastExitCode = res.exitCode,
                )
            } else {
                val root = TerminalShellEngine.syncWorkspaceToDisk(workspaceRootDir, wsId, files)
                appendTerminalLine("Synced ${files.size} workspace files to ${root.absolutePath}", TerminalStream.SYSTEM)
            }
        }
    }

    fun runTerminalCommand(rawCommand: String) {
        val cmd = rawCommand.trim()
        if (cmd.isBlank()) return
        val profile = (profileState.value as? UiState.Success)?.data ?: AgentProfile(userId = currentUserId)
        val files = (filesState.value as? UiState.Success)?.data.orEmpty()

        if (cmd.equals("clear", ignoreCase = true)) {
            clearTerminalOutput()
            return
        }

        val currentHistory = _terminalUiState.value.commandHistory
        val updatedHistory = if (currentHistory.lastOrNull() == cmd) currentHistory else (currentHistory + cmd).takeLast(50)
        _terminalUiState.value = _terminalUiState.value.copy(commandHistory = updatedHistory)
        appendTerminalLine("$ $cmd", TerminalStream.COMMAND)

        if (profile.blockDestructiveHooks && (cmd.contains("rm -rf") || cmd.contains("mkfs") || cmd.contains(":(){"))) {
            val msg = "BLOCKED by @hooks.pre_turn: HookResult(allow=False, reason=\"Destructive commands are restricted.\")"
            appendTerminalLine(msg, TerminalStream.STDERR)
            _terminalUiState.value = _terminalUiState.value.copy(lastExitCode = 126)
            _terminalEntries.value = (_terminalEntries.value + TerminalEntry(
                id = "term_${System.currentTimeMillis()}",
                command = cmd,
                output = msg,
                isError = true,
            )).takeLast(40)
            return
        }

        if (!profile.enabledTools.contains(BuiltinTool.RUN_COMMAND.name) || profile.readOnlyMode) {
            val msg = "DENIED: BuiltinTools.RUN_COMMAND is disabled in LocalAgentConfig (readOnlyMode=${profile.readOnlyMode})."
            appendTerminalLine(msg, TerminalStream.STDERR)
            _terminalUiState.value = _terminalUiState.value.copy(lastExitCode = 126)
            _terminalEntries.value = (_terminalEntries.value + TerminalEntry(
                id = "term_${System.currentTimeMillis()}",
                command = cmd,
                output = msg,
                isError = true,
            )).takeLast(40)
            return
        }

        val builtinOutput = when {
            cmd == "help" ->
                "Built-ins: antigravity status | pip show google-antigravity | git diff | clear\nOS Shell (/system/bin/sh): ls -la | pwd | cd <dir> | cat <file> | grep | find | env | uname -a\nTermux Live: switch to 'Termux Live' mode to run python3, git, gh, pkg, or gradle assembleDebug."
            cmd == "termux" ->
                "Switch to 'Termux Live' in the Terminal tab (or tap 'Termux Bridge') to connect Termux over 127.0.0.1:8766 or export full workspace scripts."
            cmd.startsWith("antigravity status") ->
                "Model: ${profile.preferredModel}\nMode: ${profile.agentMode}\nReadOnly: ${profile.readOnlyMode}\nHooks: ${if (profile.blockDestructiveHooks) "pre_turn active" else "none"}\nFiles: ${files.size} synced in Firestore"
            cmd.startsWith("pip show google-antigravity") ->
                "Name: google-antigravity\nVersion: 1.0.4\nSummary: Google Antigravity Agentic Runtime SDK (Android Bridge)\nRequires: google-genai, firebase-ai-logic"
            cmd.startsWith("git diff") -> {
                val modified = files.filter { it.previousContent.isNotBlank() && it.previousContent != it.content }
                if (modified.isEmpty()) "Working tree clean."
                else modified.joinToString("\n\n") { AntigravitySeedData.buildUnifiedDiff(it.previousContent, it.content, it.path) }
            }
            else -> null
        }

        if (builtinOutput != null) {
            builtinOutput.lines().forEach { appendTerminalLine(it, TerminalStream.STDOUT) }
            _terminalUiState.value = _terminalUiState.value.copy(lastExitCode = 0)
            _terminalEntries.value = (_terminalEntries.value + TerminalEntry(
                id = "term_${System.currentTimeMillis()}",
                command = cmd,
                output = builtinOutput,
            )).takeLast(40)
            return
        }

        terminalExecJob?.cancel()
        terminalExecJob = viewModelScope.launch {
            _terminalUiState.value = _terminalUiState.value.copy(isRunning = true)
            val collected = mutableListOf<String>()
            var hadError = false
            try {
                val snap = _terminalUiState.value
                val result = if (snap.mode == TerminalTargetMode.TERMUX_BRIDGE) {
                    TerminalShellEngine.executeTermuxBridge(
                        bridgeUrl = snap.termuxBridgeUrl,
                        token = snap.termuxBridgeToken,
                        command = cmd,
                    ).also { res ->
                        res.outputLines.forEach { (line, stream) ->
                            appendTerminalLine(line, stream)
                            collected += line
                            if (stream == TerminalStream.STDERR) hadError = true
                        }
                    }
                } else {
                    val wsDir = TerminalShellEngine.syncWorkspaceToDisk(
                        workspaceRootDir = workspaceRootDir,
                        workspaceId = _selectedWorkspaceId.value,
                        files = files,
                    )
                    TerminalShellEngine.executeSandboxShell(
                        workspaceDir = wsDir,
                        currentRelativeDir = snap.currentRelativeDir,
                        rawCommand = cmd,
                    ) { line, stream ->
                        appendTerminalLine(line, stream)
                        collected += line
                        if (stream == TerminalStream.STDERR) hadError = true
                    }
                }

                _terminalUiState.value = _terminalUiState.value.copy(
                    isRunning = false,
                    currentRelativeDir = result.newRelativeDir,
                    termuxCwd = result.termuxCwd ?: _terminalUiState.value.termuxCwd,
                    termuxConnected = if (snap.mode == TerminalTargetMode.TERMUX_BRIDGE && result.termuxCwd != null) true else _terminalUiState.value.termuxConnected,
                    lastExitCode = result.exitCode,
                )
                val summaryOut = collected.takeLast(12).joinToString("\n").ifBlank { "(exit ${result.exitCode})" }
                _terminalEntries.value = (_terminalEntries.value + TerminalEntry(
                    id = "term_${System.currentTimeMillis()}",
                    command = cmd,
                    output = summaryOut,
                    isError = result.exitCode != 0 || hadError,
                )).takeLast(40)
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (_terminalUiState.value.isRunning) {
                    _terminalUiState.value = _terminalUiState.value.copy(isRunning = false)
                }
            }
        }
    }

    fun setApkBuildSource(source: ApkBuildSource) {
        stopApkBuildPolling()
        _apkBuildState.value = _apkBuildState.value.copy(
            source = source,
            errorMessage = null,
        )
    }

    fun updateApkGitHubRepo(repo: String) {
        _apkBuildState.value = _apkBuildState.value.copy(githubRepo = repo)
    }

    fun updateApkGitHubToken(token: String) {
        _apkBuildState.value = _apkBuildState.value.copy(githubToken = token)
    }

    fun updateApkLocalUrl(url: String) {
        _apkBuildState.value = _apkBuildState.value.copy(localStatusUrl = url)
    }

    fun refreshApkBuildOnce() {
        viewModelScope.launch {
            performBuildPollStep()
        }
    }

    fun toggleApkBuildPolling() {
        if (_apkBuildState.value.isPolling) {
            stopApkBuildPolling()
        } else {
            startApkBuildPolling()
        }
    }

    private fun startApkBuildPolling() {
        buildPollingJob?.cancel()
        _apkBuildState.value = _apkBuildState.value.copy(isPolling = true, errorMessage = null)
        buildPollingJob = viewModelScope.launch {
            while (isActive && _apkBuildState.value.isPolling) {
                performBuildPollStep()
                val current = _apkBuildState.value
                if (!current.isPolling) break
                val intervalMs = if (current.source == ApkBuildSource.TERMUX_LOCAL) 2000L else 5000L
                delay(intervalMs)
            }
        }
    }

    private fun stopApkBuildPolling() {
        buildPollingJob?.cancel()
        buildPollingJob = null
        _apkBuildState.value = _apkBuildState.value.copy(isPolling = false)
    }

    private suspend fun performBuildPollStep() {
        val snapshot = _apkBuildState.value.copy(isRefreshing = true)
        _apkBuildState.value = snapshot
        val updated = when (snapshot.source) {
            ApkBuildSource.GITHUB_ACTIONS -> buildMonitorClient.fetchGitHubBuildStatus(snapshot)
            ApkBuildSource.TERMUX_LOCAL -> buildMonitorClient.fetchTermuxLocalStatus(snapshot)
        }
        _apkBuildState.value = updated
        if (!updated.isPolling) {
            buildPollingJob?.cancel()
            buildPollingJob = null
        }
    }
}
