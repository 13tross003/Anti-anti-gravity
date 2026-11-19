package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AntigravityAiEngine
import com.example.data.AntigravityRepository
import com.example.firebase.authStateFlow
import com.example.firebase.signOut
import com.example.model.AgentProfile
import com.example.model.AgentSession
import com.example.model.ApprovalStatus
import com.example.model.Artifact
import com.example.model.SessionStatus
import com.example.model.UiState
import com.example.ui.components.AntigravityLogoMark
import com.example.ui.theme.AntigravityTheme
import com.example.viewmodel.AntigravityViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

private enum class AntigravityDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    AGENTS("Agents", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_agents"),
    EDITOR("Editor", Icons.Filled.Code, Icons.Outlined.Code, "nav_editor"),
    TERMINAL("Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_terminal"),
    ARTIFACTS("Artifacts", Icons.Filled.Layers, Icons.Outlined.Layers, "nav_artifacts"),
    BROWSER_SDK("Browser/SDK", Icons.Filled.Tune, Icons.Outlined.Tune, "nav_browser_sdk"),
}

@Composable
fun AntigravityApp() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AntigravityTheme(darkTheme = true) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        key(user.uid) {
            val viewModel: AntigravityViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        AntigravityViewModel(
                            repository = AntigravityRepository(db, auth),
                            aiEngine = AntigravityAiEngine(),
                            currentUserId = user.uid,
                            displayName = user.displayName ?: "Antigravity Dev",
                            workspaceRootDir = File(app.filesDir, "antigravity_workspaces"),
                        )
                    }
                }
            )
            AntigravityWorkspaceShell(
                viewModel = viewModel,
                userId = user.uid,
                userEmail = user.email,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AntigravityWorkspaceShell(
    viewModel: AntigravityViewModel,
    userId: String,
    userEmail: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val workspacesState by viewModel.workspacesState.collectAsStateWithLifecycle()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    val filesState by viewModel.filesState.collectAsStateWithLifecycle()
    val selectedFileId by viewModel.selectedFileId.collectAsStateWithLifecycle()
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val artifactsState by viewModel.artifactsState.collectAsStateWithLifecycle()
    val selectedArtifactId by viewModel.selectedArtifactId.collectAsStateWithLifecycle()
    val isAgentRunning by viewModel.isAgentRunning.collectAsStateWithLifecycle()
    val inlineAiResponse by viewModel.inlineAiResponse.collectAsStateWithLifecycle()
    val isInlineBusy by viewModel.isInlineBusy.collectAsStateWithLifecycle()
    val terminalEntries by viewModel.terminalEntries.collectAsStateWithLifecycle()
    val terminalUiState by viewModel.terminalUiState.collectAsStateWithLifecycle()
    val browserUrl by viewModel.browserUrl.collectAsStateWithLifecycle()
    val browserHtmlPreview by viewModel.browserHtmlPreview.collectAsStateWithLifecycle()
    val browserLogs by viewModel.browserLogs.collectAsStateWithLifecycle()
    val bannerError by viewModel.bannerError.collectAsStateWithLifecycle()
    val statusNotice by viewModel.statusNotice.collectAsStateWithLifecycle()
    val apkBuildState by viewModel.apkBuildState.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data ?: AgentProfile(userId = userId)
    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val artifacts = (artifactsState as? UiState.Success)?.data.orEmpty()

    val needsReviewSessions = sessions.count { SessionStatus.safeParse(it.status) == SessionStatus.NEEDS_REVIEW }
    val pendingArtifacts = artifacts.count { ApprovalStatus.safeParse(it.approvalStatus) == ApprovalStatus.PENDING }

    var activeDestination by remember { mutableIntStateOf(0) }
    var browserTabIndex by remember { mutableIntStateOf(0) }
    var showApkBuildMonitor by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bannerError, statusNotice) {
        val msg = bannerError ?: statusNotice
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotices()
        }
    }

    AntigravityTheme(darkTheme = profile.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AntigravityLogoMark(size = 24.dp)
                                Column {
                                    Text(
                                        text = "Google Antigravity",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = AntigravityDestination.entries[activeDestination].label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        },
                        actions = {
                            ApkBuildTopBarPill(
                                state = apkBuildState,
                                onClick = { showApkBuildMonitor = true },
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                Text(
                                    text = profile.preferredModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            AntigravityDestination.entries.forEachIndexed { index, dest ->
                                val badgeCount = when (dest) {
                                    AntigravityDestination.AGENTS -> needsReviewSessions
                                    AntigravityDestination.ARTIFACTS -> pendingArtifacts
                                    else -> 0
                                }
                                NavigationBarItem(
                                    selected = activeDestination == index,
                                    onClick = { activeDestination = index },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (badgeCount > 0) {
                                                    Badge { Text(badgeCount.toString()) }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (activeDestination == index) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label,
                                            )
                                        }
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Spacer(Modifier.size(8.dp))
                            AntigravityDestination.entries.forEachIndexed { index, dest ->
                                val badgeCount = when (dest) {
                                    AntigravityDestination.AGENTS -> needsReviewSessions
                                    AntigravityDestination.ARTIFACTS -> pendingArtifacts
                                    else -> 0
                                }
                                NavigationRailItem(
                                    selected = activeDestination == index,
                                    onClick = { activeDestination = index },
                                    icon = {
                                        BadgedBox(
                                            badge = {
                                                if (badgeCount > 0) {
                                                    Badge { Text(badgeCount.toString()) }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (activeDestination == index) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label,
                                            )
                                        }
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag),
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 900.dp)
                        ) {
                            when (AntigravityDestination.entries[activeDestination]) {
                                AntigravityDestination.AGENTS -> AgentManagerScreen(
                                    profile = profile,
                                    workspacesState = workspacesState,
                                    selectedWorkspaceId = selectedWorkspaceId,
                                    sessionsState = sessionsState,
                                    selectedSessionId = selectedSessionId,
                                    messagesState = messagesState,
                                    isAgentRunning = isAgentRunning,
                                    onSelectWorkspace = viewModel::selectWorkspace,
                                    onCreateWorkspace = viewModel::createWorkspace,
                                    onDispatchSession = viewModel::dispatchNewSession,
                                    onOpenSession = viewModel::openSession,
                                    onSendFollowUp = { session: AgentSession, text: String, forceExec: Boolean ->
                                        viewModel.sendSessionMessage(session, text, forceExec)
                                    },
                                    onNavigateToArtifact = { artifactId ->
                                        viewModel.selectArtifact(artifactId)
                                        activeDestination = AntigravityDestination.ARTIFACTS.ordinal
                                    },
                                    onToggleAgentMode = { mode ->
                                        viewModel.updateProfileConfig(agentMode = mode)
                                    },
                                )

                                AntigravityDestination.EDITOR -> EditorScreen(
                                    profile = profile,
                                    workspaceId = selectedWorkspaceId,
                                    filesState = filesState,
                                    selectedFileId = selectedFileId,
                                    inlineAiResponse = inlineAiResponse,
                                    isInlineBusy = isInlineBusy,
                                    terminalEntries = terminalEntries,
                                    onSelectFile = viewModel::selectFile,
                                    onSaveFile = { existing, path, lang, content ->
                                        viewModel.saveWorkspaceFile(selectedWorkspaceId, existing, path, lang, content)
                                    },
                                    onDeleteFile = { fileId ->
                                        viewModel.deleteWorkspaceFile(selectedWorkspaceId, fileId)
                                    },
                                    onInlineAsk = { file, instruction ->
                                        viewModel.runInlineAsk(file, instruction, profile.preferredModel)
                                    },
                                    onApplyInlineEdit = { file, instruction ->
                                        viewModel.applyInlineEdit(selectedWorkspaceId, file, instruction, profile.preferredModel)
                                    },
                                    onRunTerminalCommand = viewModel::runTerminalCommand,
                                    onOpenBuildMonitor = { showApkBuildMonitor = true },
                                    onOpenFullTerminal = {
                                        activeDestination = AntigravityDestination.TERMINAL.ordinal
                                    },
                                )

                                AntigravityDestination.TERMINAL -> TerminalEmulatorScreen(
                                    profile = profile,
                                    workspaceId = selectedWorkspaceId,
                                    terminalState = terminalUiState,
                                    onSelectMode = viewModel::setTerminalMode,
                                    onUpdateBridgeUrl = viewModel::updateTermuxBridgeUrl,
                                    onTestTermuxConnection = viewModel::testTermuxBridgeConnection,
                                    onSyncWorkspace = viewModel::syncTerminalWorkspace,
                                    onExecuteCommand = viewModel::runTerminalCommand,
                                    onStopExecution = viewModel::stopTerminalExecution,
                                    onClearTerminal = viewModel::clearTerminalOutput,
                                    onOpenBuildMonitor = { showApkBuildMonitor = true },
                                )

                                AntigravityDestination.ARTIFACTS -> ArtifactsScreen(
                                    artifactsState = artifactsState,
                                    selectedArtifactId = selectedArtifactId,
                                    onSelectArtifact = viewModel::selectArtifact,
                                    onSetApproval = viewModel::setArtifactApproval,
                                    onPreviewHtmlInBrowser = { artifact: Artifact ->
                                        viewModel.openHtmlInBrowser(artifact.title, artifact.content)
                                        browserTabIndex = 0
                                        activeDestination = AntigravityDestination.BROWSER_SDK.ordinal
                                    },
                                )

                                AntigravityDestination.BROWSER_SDK -> BrowserAndSdkScreen(
                                    profile = profile,
                                    userEmail = userEmail,
                                    browserUrl = browserUrl,
                                    browserHtmlPreview = browserHtmlPreview,
                                    browserLogs = browserLogs,
                                    initialTab = browserTabIndex,
                                    onNavigateUrl = { url -> viewModel.navigateBrowserUrl(url, profile.browserAllowlist) },
                                    onAppendBrowserLog = viewModel::appendBrowserLog,
                                    onSendToAgent = {
                                        viewModel.sendBrowserContextToAgent(browserUrl, browserHtmlPreview, browserLogs)
                                        activeDestination = AntigravityDestination.AGENTS.ordinal
                                    },
                                    onUpdateModel = { modelId -> viewModel.updateProfileConfig(preferredModel = modelId) },
                                    onUpdateMode = { mode -> viewModel.updateProfileConfig(agentMode = mode) },
                                    onToggleReadOnly = { ro -> viewModel.updateProfileConfig(readOnlyMode = ro) },
                                    onToggleTool = { tool -> viewModel.toggleTool(tool, profile.enabledTools) },
                                    onToggleDestructiveHooks = { enabled ->
                                        viewModel.updateProfileConfig(blockDestructiveHooks = enabled)
                                    },
                                    onToggleDarkTheme = { dark -> viewModel.updateProfileConfig(darkTheme = dark) },
                                    onAddAllowlistDomain = { domain ->
                                        val next = (profile.browserAllowlist + domain).distinct().take(20)
                                        viewModel.updateProfileConfig(browserAllowlist = next)
                                    },
                                    onRemoveAllowlistDomain = { domain ->
                                        val next = profile.browserAllowlist.filterNot { it == domain }
                                        viewModel.updateProfileConfig(browserAllowlist = next)
                                    },
                                    onNotifyCopied = {
                                        viewModel.appendBrowserLog("[SDK] Copied LocalAgentConfig Python script to clipboard.")
                                    },
                                    onSignOut = {
                                        signOut(credentialManager = credentialManager, scope = scope)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (showApkBuildMonitor) {
                ApkBuildMonitorSheet(
                    state = apkBuildState,
                    onDismiss = { showApkBuildMonitor = false },
                    onSelectSource = viewModel::setApkBuildSource,
                    onUpdateRepo = viewModel::updateApkGitHubRepo,
                    onUpdateToken = viewModel::updateApkGitHubToken,
                    onUpdateLocalUrl = viewModel::updateApkLocalUrl,
                    onRefreshOnce = viewModel::refreshApkBuildOnce,
                    onTogglePolling = viewModel::toggleApkBuildPolling,
                )
            }
        }
    }
}
