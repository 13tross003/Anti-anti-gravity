package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AgentMessage
import com.example.model.AgentMode
import com.example.model.AgentProfile
import com.example.model.AgentSession
import com.example.model.MessageRole
import com.example.model.SessionStatus
import com.example.model.ToolStatus
import com.example.model.UiState
import com.example.model.Workspace
import com.example.ui.components.SessionStatusChip
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAntigravityColors

private enum class InboxFilter(val label: String) {
    ALL("All"),
    NEEDS_REVIEW("Needs Review"),
    RUNNING("Running"),
    COMPLETED("Completed"),
}

@Composable
fun AgentManagerScreen(
    profile: AgentProfile,
    workspacesState: UiState<List<Workspace>>,
    selectedWorkspaceId: String,
    sessionsState: UiState<List<AgentSession>>,
    selectedSessionId: String?,
    messagesState: UiState<List<AgentMessage>>,
    isAgentRunning: Boolean,
    onSelectWorkspace: (String) -> Unit,
    onCreateWorkspace: (String, String, String) -> Unit,
    onDispatchSession: (String) -> Unit,
    onOpenSession: (String?) -> Unit,
    onSendFollowUp: (AgentSession, String, Boolean) -> Unit,
    onNavigateToArtifact: (String) -> Unit,
    onToggleAgentMode: (AgentMode) -> Unit,
    apkSourceInput: String = "",
    onApkSourceChanged: (String) -> Unit = {},
) {
    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val activeSession = sessions.firstOrNull { it.id == selectedSessionId }

    if (activeSession != null) {
        AgentSessionDetailView(
            session = activeSession,
            messagesState = messagesState,
            isAgentRunning = isAgentRunning,
            onBack = { onOpenSession(null) },
            onSendMessage = { text -> onSendFollowUp(activeSession, text, false) },
            onApproveAndExecute = {
                onSendFollowUp(
                    activeSession,
                    "Approved the implementation plan. Execute the changes and update workspace files now.",
                    true,
                )
            },
            onNavigateToArtifact = onNavigateToArtifact,
        )
        return
    }

    var dispatchPrompt by remember { mutableStateOf("") }
    var inboxFilter by remember { mutableStateOf(InboxFilter.ALL) }
    var showNewWorkspaceDialog by remember { mutableStateOf(false) }

    val workspaces = (workspacesState as? UiState.Success)?.data.orEmpty()
    val currentMode = AgentMode.safeParse(profile.agentMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        // Workspace switcher strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            workspaces.forEach { ws ->
                FilterChip(
                    selected = ws.id == selectedWorkspaceId,
                    onClick = { onSelectWorkspace(ws.id) },
                    label = { Text(ws.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("workspace_chip_${ws.id}"),
                )
            }
            OutlinedButton(
                onClick = { showNewWorkspaceDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("new_workspace_button"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create workspace", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Workspace", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Mission Control Quick Dispatch Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Dispatch Autonomous Agent",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clickable {
                                val next = if (currentMode == AgentMode.PLANNING) AgentMode.FAST else AgentMode.PLANNING
                                onToggleAgentMode(next)
                            }
                            .testTag("toggle_agent_mode_pill"),
                    ) {
                        Text(
                            text = "${currentMode.label} Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = dispatchPrompt,
                    onValueChange = { dispatchPrompt = it },
                    placeholder = {
                        Text("e.g. Build a responsive HTML status dashboard or add @hooks.on_tool_error retry")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dispatch_prompt_input"),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(8.dp))

                // One-tap starter prompts so anyone can launch an agent effortlessly
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "Build HTML UI" to "Create an interactive HTML/CSS status widget in preview.html with live metrics",
                        "Explain Workspace" to "Explain every file in this workspace in simple terms",
                        "Add Retry Hook" to "Add an @hooks.on_tool_error retry handler to agent_quickstart.py",
                        "Security Audit" to "Audit the workspace for security best practices and summarize findings",
                    ).forEach { (chipLabel, chipPrompt) ->
                        FilterChip(
                            selected = false,
                            onClick = { dispatchPrompt = chipPrompt },
                            label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${profile.preferredModel} • ${if (profile.readOnlyMode) "Read-Only" else "${profile.enabledTools.size} tools"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (dispatchPrompt.isNotBlank()) {
                                val p = dispatchPrompt
                                dispatchPrompt = ""
                                onDispatchSession(p)
                            }
                        },
                        enabled = dispatchPrompt.isNotBlank() && !isAgentRunning,
                        modifier = Modifier.testTag("dispatch_agent_button"),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        if (isAgentRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Launching...")
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Spawn Agent")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Inbox Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InboxFilter.entries.forEach { filter ->
                val count = when (filter) {
                    InboxFilter.ALL -> sessions.size
                    InboxFilter.NEEDS_REVIEW -> sessions.count { SessionStatus.safeParse(it.status) == SessionStatus.NEEDS_REVIEW }
                    InboxFilter.RUNNING -> sessions.count { SessionStatus.safeParse(it.status) == SessionStatus.RUNNING }
                    InboxFilter.COMPLETED -> sessions.count { SessionStatus.safeParse(it.status) == SessionStatus.COMPLETED }
                }
                FilterChip(
                    selected = inboxFilter == filter,
                    onClick = { inboxFilter = filter },
                    label = { Text("${filter.label} ($count)") },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (sessionsState) {
            UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = sessionsState.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            is UiState.Success -> {
                val filtered = sessions.filter { s ->
                    val status = SessionStatus.safeParse(s.status)
                    when (inboxFilter) {
                        InboxFilter.ALL -> true
                        InboxFilter.NEEDS_REVIEW -> status == SessionStatus.NEEDS_REVIEW
                        InboxFilter.RUNNING -> status == SessionStatus.RUNNING
                        InboxFilter.COMPLETED -> status == SessionStatus.COMPLETED
                    }
                }

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "No agent sessions in ${inboxFilter.label}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Dispatch a new task above to start an Antigravity agent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { session ->
                            AgentSessionCard(
                                session = session,
                                onClick = { onOpenSession(session.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewWorkspaceDialog) {
        NewWorkspaceDialog(
            onDismiss = { showNewWorkspaceDialog = false },
            onCreate = { name, desc, lang ->
                showNewWorkspaceDialog = false
                onCreateWorkspace(name, desc, lang)
            },
        )
    }
}

@Composable
private fun AgentSessionCard(
    session: AgentSession,
    onClick: () -> Unit,
) {
    val status = SessionStatus.safeParse(session.status)
    val progress = if (session.tasksTotal > 0) {
        (session.tasksCompleted.toFloat() / session.tasksTotal.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.workspaceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                SessionStatusChip(status = status)
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (session.summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = session.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp),
                )
                Text(
                    text = "${session.tasksCompleted}/${session.tasksTotal} tasks • ${session.mode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AgentSessionDetailView(
    session: AgentSession,
    messagesState: UiState<List<AgentMessage>>,
    isAgentRunning: Boolean,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onApproveAndExecute: () -> Unit,
    onNavigateToArtifact: (String) -> Unit,
) {
    BackHandler(onBack = onBack)

    var replyInput by remember { mutableStateOf("") }
    val status = SessionStatus.safeParse(session.status)
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Session Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("session_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Agent Manager")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${session.workspaceName} • ${session.modelName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SessionStatusChip(status = status)
            }
        }

        // Needs Review Banner for Planning Mode
        AnimatedVisibility(visible = status == SessionStatus.NEEDS_REVIEW && !isAgentRunning) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Plan ready for approval",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = onApproveAndExecute,
                        modifier = Modifier.testTag("approve_execute_plan_button"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Approve & Execute")
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                AgentMessageBubble(
                    message = msg,
                    onOpenArtifact = onNavigateToArtifact,
                )
            }

            if (isAgentRunning) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Antigravity agent executing turn with ${session.modelName}...",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        // Composer
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = replyInput,
                    onValueChange = { replyInput = it },
                    placeholder = { Text("Instruct agent or request changes...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("session_reply_input"),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                )
                IconButton(
                    onClick = {
                        if (replyInput.isNotBlank()) {
                            val text = replyInput
                            replyInput = ""
                            onSendMessage(text)
                        }
                    },
                    enabled = replyInput.isNotBlank() && !isAgentRunning,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("session_send_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send prompt")
                }
            }
        }
    }
}

@Composable
private fun AgentMessageBubble(
    message: AgentMessage,
    onOpenArtifact: (String) -> Unit,
) {
    val role = MessageRole.safeParse(message.role)
    val ext = LocalAntigravityColors.current

    when (role) {
        MessageRole.TOOL -> {
            val toolStatus = ToolStatus.safeParse(message.toolStatus)
            val tint = when (toolStatus) {
                ToolStatus.BLOCKED, ToolStatus.ERROR -> ext.statusError
                ToolStatus.SUCCESS -> ext.statusCompleted
                else -> ext.statusRunning
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ext.codeBackground,
                border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = if (toolStatus == ToolStatus.BLOCKED) Icons.Rounded.Security else Icons.Rounded.Build,
                        contentDescription = message.toolName,
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        if (message.toolName.isNotBlank()) {
                            Text(
                                text = message.toolName,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                            )
                        }
                        Text(
                            text = message.content,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        MessageRole.USER -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(0.88f),
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        MessageRole.AGENT, MessageRole.SYSTEM -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (role == MessageRole.SYSTEM) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (role == MessageRole.SYSTEM) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    if (message.artifactId.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onOpenArtifact(message.artifactId) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Review Artifact", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewWorkspaceDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Python") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Workspace") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    placeholder = { Text("e.g. my-agent-service") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Primary Stack") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, language) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
