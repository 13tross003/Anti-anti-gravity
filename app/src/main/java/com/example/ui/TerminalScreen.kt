package com.example.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TerminalShellEngine
import com.example.data.TerminalStream
import com.example.data.TerminalTargetMode
import com.example.data.TerminalUiState
import com.example.model.AgentProfile
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAntigravityColors

@Composable
fun TerminalEmulatorScreen(
    profile: AgentProfile,
    workspaceId: String,
    terminalState: TerminalUiState,
    onSelectMode: (TerminalTargetMode) -> Unit,
    onUpdateBridgeUrl: (String) -> Unit,
    onTestTermuxConnection: () -> Unit,
    onSyncWorkspace: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onStopExecution: () -> Unit,
    onClearTerminal: () -> Unit,
    onOpenBuildMonitor: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val ext = LocalAntigravityColors.current
    val listState = rememberLazyListState()

    var commandInput by remember { mutableStateOf("") }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var showListenerSetup by remember(terminalState.mode) {
        mutableStateOf(terminalState.mode == TerminalTargetMode.TERMUX_BRIDGE && !terminalState.termuxConnected)
    }
    var copiedListener by remember { mutableStateOf(false) }

    val listenerSnippet = remember(terminalState.termuxBridgeToken) {
        TerminalShellEngine.buildTermuxListenerCommand(
            port = 8766,
            token = terminalState.termuxBridgeToken,
        )
    }

    LaunchedEffect(terminalState.lines.size) {
        if (terminalState.lines.isNotEmpty()) {
            listState.animateScrollToItem(terminalState.lines.lastIndex)
        }
    }

    val quickCommands = remember(terminalState.mode) {
        if (terminalState.mode == TerminalTargetMode.TERMUX_BRIDGE) {
            listOf("ls -la", "pwd", "python3 --version", "git status", "gradle assembleDebug", "pkg list-installed", "clear")
        } else {
            listOf("ls -la", "pwd", "antigravity status", "git diff", "uname -a", "env", "find . -maxdepth 2", "clear")
        }
    }

    fun submitCommand() {
        val cmd = commandInput.trim()
        if (cmd.isNotBlank() && !terminalState.isRunning) {
            commandInput = ""
            historyCursor = -1
            onExecuteCommand(cmd)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Environment Switcher & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalTargetMode.entries.forEach { mode ->
                FilterChip(
                    selected = terminalState.mode == mode,
                    onClick = { onSelectMode(mode) },
                    leadingIcon = {
                        if (mode == TerminalTargetMode.TERMUX_BRIDGE) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (terminalState.termuxConnected) ext.statusCompleted else ext.statusReview)
                            )
                        } else {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.testTag("terminal_mode_${mode.name.lowercase()}"),
                )
            }

            OutlinedButton(
                onClick = onSyncWorkspace,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("terminal_sync_workspace_button"),
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (terminalState.mode == TerminalTargetMode.TERMUX_BRIDGE) "Sync to Termux" else "Sync Disk",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            OutlinedButton(
                onClick = onOpenBuildMonitor,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("terminal_apk_monitor_button"),
            ) {
                Text("APK Monitor", style = MaterialTheme.typography.labelSmall)
            }

            IconButton(
                onClick = onClearTerminal,
                modifier = Modifier.testTag("terminal_clear_button"),
            ) {
                Icon(Icons.Rounded.ClearAll, contentDescription = "Clear terminal")
            }
        }

        // Termux Live Bridge Connection Panel
        if (terminalState.mode == TerminalTargetMode.TERMUX_BRIDGE) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    if (terminalState.termuxConnected) ext.statusCompleted.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (terminalState.termuxConnected) {
                                "Termux Live Connected • ${terminalState.termuxCwd}"
                            } else {
                                "Connect to Termux Live Listener (127.0.0.1:8766)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (terminalState.termuxConnected) ext.statusCompleted else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { showListenerSetup = !showListenerSetup },
                            modifier = Modifier.testTag("toggle_termux_listener_setup_button"),
                        ) {
                            Icon(
                                imageVector = if (showListenerSetup) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle Termux listener setup",
                            )
                        }
                    }

                    AnimatedVisibility(visible = showListenerSetup) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = terminalState.termuxBridgeUrl,
                                    onValueChange = onUpdateBridgeUrl,
                                    label = { Text("Termux Bridge URL") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("termux_bridge_url_input"),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                )
                                Button(
                                    onClick = onTestTermuxConnection,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("termux_test_connection_button"),
                                ) {
                                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Connect")
                                }
                            }

                            Text(
                                text = "Run this one-time listener inside Termux so commands execute live from this terminal:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(listenerSnippet))
                                        copiedListener = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("copy_termux_listener_button"),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (copiedListener) "Listener Copied" else "Copy Listener")
                                }
                                Button(
                                    onClick = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, listenerSnippet)
                                            putExtra(Intent.EXTRA_SUBJECT, "Antigravity Termux Live Bridge")
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(sendIntent, "Send listener to Termux"))
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("send_termux_listener_button"),
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Send to Termux")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Terminal Status Header + Obsidian Console Viewport
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ext.codeBackground,
            border = BorderStroke(1.dp, ext.codeBorder),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Status Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cwdLabel = if (terminalState.mode == TerminalTargetMode.TERMUX_BRIDGE) {
                        "termux:${terminalState.termuxCwd}"
                    } else {
                        "sandbox:/$workspaceId/${terminalState.currentRelativeDir}".replace("/.", "")
                    }
                    Text(
                        text = cwdLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (profile.blockDestructiveHooks) "hook:guard" else "hook:off",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (profile.blockDestructiveHooks) ext.statusCompleted else ext.statusReview,
                        )
                        terminalState.lastExitCode?.let { code ->
                            Text(
                                text = "exit $code",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (code == 0) ext.statusCompleted else ext.statusError,
                            )
                        }
                    }
                }

                if (terminalState.isRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .testTag("terminal_running_indicator"),
                    )
                }

                SelectionContainer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(terminalState.lines, key = { it.id }) { line ->
                            val lineColor = when (line.stream) {
                                TerminalStream.COMMAND -> ext.syntaxKeyword
                                TerminalStream.STDOUT -> MaterialTheme.colorScheme.onSurface
                                TerminalStream.STDERR -> ext.statusError
                                TerminalStream.SYSTEM -> ext.statusCompleted
                            }
                            Text(
                                text = line.text,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (line.stream == TerminalStream.COMMAND) FontWeight.SemiBold else FontWeight.Normal,
                                color = lineColor,
                            )
                        }
                    }
                }
            }
        }

        // Quick Command Shortcuts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickCommands.forEach { cmd ->
                SuggestionChip(
                    onClick = {
                        if (cmd == "clear") {
                            onClearTerminal()
                        } else if (!terminalState.isRunning) {
                            onExecuteCommand(cmd)
                        }
                    },
                    label = {
                        Text(
                            text = cmd,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                        )
                    },
                    modifier = Modifier.testTag("quick_cmd_${cmd.replace(" ", "_")}"),
                )
            }
        }

        // Prompt Input Bar + History Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = {
                    val hist = terminalState.commandHistory
                    if (hist.isNotEmpty()) {
                        val nextIdx = (historyCursor + 1).coerceAtMost(hist.lastIndex)
                        historyCursor = nextIdx
                        commandInput = hist[hist.lastIndex - nextIdx]
                    }
                },
                enabled = terminalState.commandHistory.isNotEmpty(),
                modifier = Modifier.testTag("terminal_history_up_button"),
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Previous command")
            }

            IconButton(
                onClick = {
                    val hist = terminalState.commandHistory
                    if (historyCursor > 0) {
                        val nextIdx = historyCursor - 1
                        historyCursor = nextIdx
                        commandInput = hist[hist.lastIndex - nextIdx]
                    } else {
                        historyCursor = -1
                        commandInput = ""
                    }
                },
                enabled = historyCursor >= 0,
                modifier = Modifier.testTag("terminal_history_down_button"),
            ) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Next command")
            }

            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                prefix = {
                    Text(
                        text = "$ ",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                placeholder = {
                    Text(
                        text = if (terminalState.mode == TerminalTargetMode.TERMUX_BRIDGE) {
                            "gradle assembleDebug | python3 | git..."
                        } else {
                            "ls -la | grep | find | cat | sh..."
                        },
                        fontSize = 12.sp,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitCommand() }),
                textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.5.sp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("full_terminal_command_input"),
            )

            if (terminalState.isRunning) {
                Button(
                    onClick = onStopExecution,
                    colors = ButtonDefaults.buttonColors(containerColor = ext.statusError),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("terminal_stop_button"),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = "Stop command", modifier = Modifier.size(18.dp))
                }
            } else {
                Button(
                    onClick = { submitCommand() },
                    enabled = commandInput.isNotBlank(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("terminal_execute_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Run command", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
