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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AgentProfile
import com.example.model.UiState
import com.example.model.WorkspaceFile
import com.example.ui.components.SyntaxCodeViewer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAntigravityColors
import com.example.viewmodel.TerminalEntry

@Composable
fun EditorScreen(
    profile: AgentProfile,
    workspaceId: String,
    filesState: UiState<List<WorkspaceFile>>,
    selectedFileId: String?,
    inlineAiResponse: String?,
    isInlineBusy: Boolean,
    terminalEntries: List<TerminalEntry>,
    onSelectFile: (String) -> Unit,
    onSaveFile: (WorkspaceFile?, String, String, String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onInlineAsk: (WorkspaceFile, String) -> Unit,
    onApplyInlineEdit: (WorkspaceFile, String) -> Unit,
    onRunTerminalCommand: (String) -> Unit,
    onOpenBuildMonitor: () -> Unit,
    onOpenFullTerminal: () -> Unit,
) {
    val files = (filesState as? UiState.Success)?.data.orEmpty()
    val activeFile = files.firstOrNull { it.id == selectedFileId } ?: files.firstOrNull()

    var isEditingMode by remember { mutableStateOf(false) }
    var draftContent by remember(activeFile?.id, activeFile?.content) {
        mutableStateOf(activeFile?.content ?: "")
    }
    var inlinePrompt by remember { mutableStateOf("") }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalInput by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showTermuxBridgeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeFile?.id) {
        if (activeFile != null && selectedFileId == null) {
            onSelectFile(activeFile.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // File Explorer Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            files.forEach { file ->
                FilterChip(
                    selected = file.id == activeFile?.id,
                    onClick = {
                        isEditingMode = false
                        onSelectFile(file.id)
                    },
                    label = {
                        Text(
                            text = file.path,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                        )
                    },
                    modifier = Modifier.testTag("file_tab_${file.path}"),
                )
            }
            OutlinedButton(
                onClick = { showNewFileDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("new_file_button"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New File", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("File", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Editor Action Bar
        if (activeFile != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${activeFile.path} • ${activeFile.language}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenBuildMonitor,
                        modifier = Modifier.testTag("editor_open_apk_monitor_button"),
                    ) {
                        Icon(
                            Icons.Rounded.Android,
                            contentDescription = "Open APK Build Monitor",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = { showTerminal = !showTerminal },
                        modifier = Modifier.testTag("toggle_terminal_button"),
                    ) {
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = "Toggle Terminal",
                            tint = if (showTerminal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { isEditingMode = !isEditingMode },
                        modifier = Modifier.testTag("toggle_edit_mode_button"),
                    ) {
                        Icon(
                            imageVector = if (isEditingMode) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                            contentDescription = if (isEditingMode) "Syntax View" else "Edit Source",
                        )
                    }
                    if (isEditingMode) {
                        Button(
                            onClick = {
                                onSaveFile(activeFile, activeFile.path, activeFile.language, draftContent)
                                isEditingMode = false
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("save_file_button"),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save")
                        }
                    } else if (files.size > 1) {
                        IconButton(
                            onClick = { onDeleteFile(activeFile.id) },
                            modifier = Modifier.testTag("delete_file_button"),
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete File",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        // Inline AI Command Bar (Cmd+K / Ctrl+I equivalent)
        if (activeFile != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        OutlinedTextField(
                            value = inlinePrompt,
                            onValueChange = { inlinePrompt = it },
                            placeholder = { Text("Inline AI: explain or edit ${activeFile.path}...") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("inline_ai_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isInlineBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        OutlinedButton(
                            onClick = { onInlineAsk(activeFile, inlinePrompt) },
                            enabled = inlinePrompt.isNotBlank() && !isInlineBusy,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("inline_ask_button"),
                        ) {
                            Text("Ask")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cmd = inlinePrompt
                                inlinePrompt = ""
                                onApplyInlineEdit(activeFile, cmd)
                            },
                            enabled = inlinePrompt.isNotBlank() && !isInlineBusy && !profile.readOnlyMode,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("inline_apply_edit_button"),
                        ) {
                            Text("Apply Edit")
                        }
                    }

                    AnimatedVisibility(visible = !inlineAiResponse.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Text(
                                text = inlineAiResponse.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Main Code Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (filesState) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Text(filesState.message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    if (activeFile == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tap + File to create a source file in this workspace.")
                        }
                    } else if (isEditingMode) {
                        OutlinedTextField(
                            value = draftContent,
                            onValueChange = { draftContent = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("code_editor_textfield"),
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            SyntaxCodeViewer(
                                code = activeFile.content,
                                language = activeFile.language,
                            )
                        }
                    }
                }
            }
        }

        // Collapsible Integrated Terminal Drawer
        AnimatedVisibility(visible = showTerminal) {
            val ext = LocalAntigravityColors.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ext.codeBackground)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ANTIGRAVITY TERMINAL • @hooks.pre_turn ${if (profile.blockDestructiveHooks) "ON" else "OFF"}",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onOpenFullTerminal,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("expand_full_terminal_button"),
                        ) {
                            Text("Full View", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { showTermuxBridgeDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("termux_bridge_button"),
                        ) {
                            Text("Termux Bridge", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(terminalEntries, key = { it.id }) { entry ->
                        Column {
                            Text(
                                text = "$ ${entry.command}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.5.sp,
                                color = ext.syntaxKeyword,
                            )
                            Text(
                                text = entry.output,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.5.sp,
                                color = if (entry.isError) ext.statusError else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = terminalInput,
                        onValueChange = { terminalInput = it },
                        placeholder = { Text("antigravity status | ls | git diff | python agent_quickstart.py", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_command_input"),
                        textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                    )
                    Button(
                        onClick = {
                            if (terminalInput.isNotBlank()) {
                                val c = terminalInput
                                terminalInput = ""
                                onRunTerminalCommand(c)
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("terminal_run_button"),
                    ) {
                        Text("Run")
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        NewFileDialog(
            onDismiss = { showNewFileDialog = false },
            onCreate = { path, lang ->
                showNewFileDialog = false
                onSaveFile(null, path, lang, "# $path\n")
            },
        )
    }

    if (showTermuxBridgeDialog) {
        TermuxBridgeDialog(
            workspaceId = workspaceId,
            files = files,
            activeFile = activeFile,
            onDismiss = { showTermuxBridgeDialog = false },
            onOpenBuildMonitor = {
                showTermuxBridgeDialog = false
                onOpenBuildMonitor()
            },
        )
    }
}

@Composable
private fun NewFileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("python") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Workspace File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("File Path") },
                    placeholder = { Text("e.g. tools_custom.py or widget.html") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Language") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(path, language) },
                enabled = path.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private enum class TermuxBridgeMode(val label: String) {
    CLOUD_CI_APK("Cloud CI APK"),
    ON_DEVICE_APK("On-Device APK"),
    SINGLE_FILE("Single File"),
}

private fun buildFullWorkspaceSyncCommands(
    projectDir: String,
    files: List<WorkspaceFile>,
): String {
    if (files.isEmpty()) return "mkdir -p $projectDir"
    val sb = StringBuilder()
    sb.appendLine("mkdir -p $projectDir && cd $projectDir")
    files.forEachIndexed { index, file ->
        val safePath = file.path
            .replace("\\", "/")
            .split("/")
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
            .ifBlank { "file_$index.txt" }
        var delimiter = "ANTIGRAVITY_EOF_$index"
        while (file.content.contains(delimiter)) {
            delimiter += "_X"
        }
        sb.appendLine("mkdir -p \"\$(dirname \"$safePath\")\"")
        sb.appendLine("cat << '$delimiter' > \"$safePath\"")
        sb.appendLine(file.content.trimEnd('\n'))
        sb.appendLine(delimiter)
    }
    return sb.toString().trimEnd()
}

@Composable
private fun TermuxBridgeDialog(
    workspaceId: String,
    files: List<WorkspaceFile>,
    activeFile: WorkspaceFile?,
    onDismiss: () -> Unit,
    onOpenBuildMonitor: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var mode by remember { mutableStateOf(TermuxBridgeMode.CLOUD_CI_APK) }
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    val safeWorkspaceDir = remember(workspaceId) {
        val slug = workspaceId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "workspace" }
        "~/antigravity/$slug"
    }

    val fullWorkspaceExport = remember(safeWorkspaceDir, files) {
        buildFullWorkspaceSyncCommands(safeWorkspaceDir, files)
    }

    val cloudWorkflowYaml = remember {
        """
        name: Build Android APK
        on: [push, workflow_dispatch]
        jobs:
          build:
            runs-on: ubuntu-latest
            steps:
              - uses: actions/checkout@v4
              - uses: actions/setup-java@v4
                with:
                  distribution: temurin
                  java-version: 17
              - uses: gradle/actions/setup-gradle@v4
              - run: gradle assembleDebug
              - uses: actions/upload-artifact@v4
                with:
                  name: app-debug-apk
                  path: app/build/outputs/apk/debug/*.apk
        """.trimIndent()
    }

    val cloudCiScript = remember(fullWorkspaceExport, files, cloudWorkflowYaml) {
        val hasWorkflow = files.any { it.path.contains(".github/workflows", ignoreCase = true) }
        buildString {
            appendLine(fullWorkspaceExport)
            if (!hasWorkflow) {
                appendLine("mkdir -p .github/workflows")
                appendLine("cat << 'ANTIGRAVITY_WORKFLOW_EOF' > .github/workflows/build-apk.yml")
                appendLine(cloudWorkflowYaml)
                appendLine("ANTIGRAVITY_WORKFLOW_EOF")
            }
            appendLine("git init && git add . && git commit -m \"Antigravity workspace sync\"")
            appendLine("# Run once if repo not linked: gh repo create antigravity-app --private --source=. --push")
            append("git push && gh run watch --exit-status && gh run download -n app-debug-apk")
        }
    }

    val cloudCiSetupCmd = remember {
        "pkg update -y && pkg install -y git gh && gh auth login"
    }

    val onDeviceApkScript = remember(fullWorkspaceExport) {
        buildString {
            appendLine("mkdir -p /tmp/ag_build && (pkill -f 'http.server 8765' 2>/dev/null || true)")
            appendLine("(cd /tmp/ag_build && python3 -m http.server 8765 --bind 127.0.0.1 >/dev/null 2>&1 &)")
            appendLine("cat << 'EOF' > /tmp/ag_build/build-status.json")
            appendLine("""{"phase":"compiling","progress":0.2,"stageLabel":"Syncing workspace files...","steps":[{"number":1,"name":"Sync workspace","status":"in_progress"},{"number":2,"name":"Configure Gradle","status":"queued"},{"number":3,"name":"Compile APK","status":"queued"}]}""")
            appendLine("EOF")
            appendLine(fullWorkspaceExport)
            appendLine("mkdir -p ~/.gradle")
            appendLine("grep -q 'aapt2FromMavenOverride' ~/.gradle/gradle.properties 2>/dev/null || echo \"android.aapt2FromMavenOverride=\$PREFIX/bin/aapt2\" >> ~/.gradle/gradle.properties")
            appendLine("echo \"sdk.dir=\$HOME/android-sdk\" > local.properties")
            appendLine("cat << 'EOF' > /tmp/ag_build/build-status.json")
            appendLine("""{"phase":"compiling","progress":0.55,"stageLabel":"Running gradle assembleDebug...","steps":[{"number":1,"name":"Sync workspace","status":"completed","conclusion":"success"},{"number":2,"name":"Configure Gradle","status":"completed","conclusion":"success"},{"number":3,"name":"Compile APK","status":"in_progress"}]}""")
            appendLine("EOF")
            appendLine("gradle assembleDebug > /tmp/ag_build/gradle.log 2>&1")
            appendLine("RC=${'$'}?")
            append("python3 -c \"import json,os; rc=${'$'}RC; apk='app/build/outputs/apk/debug/app-debug.apk'; ok=(rc==0 and os.path.exists(apk)); tail=''.join(open('/tmp/ag_build/gradle.log').readlines()[-12:]); json.dump({'phase':'succeeded' if ok else 'failed','progress':1.0 if ok else 0.55,'stageLabel':'APK compiled successfully' if ok else 'Gradle exited with code '+str(rc),'apkPath':apk if ok else '','apkSizeBytes':os.path.getsize(apk) if ok else 0,'logTail':tail,'steps':[{'number':1,'name':'Sync workspace','status':'completed','conclusion':'success'},{'number':2,'name':'Configure Gradle','status':'completed','conclusion':'success'},{'number':3,'name':'Compile APK','status':'completed','conclusion':'success' if ok else 'failure'}]}, open('/tmp/ag_build/build-status.json','w'))\"")
        }
    }

    val onDeviceSetupCmd = remember {
        """
        pkg update -y && pkg upgrade -y
        pkg install -y openjdk-17 gradle aapt2 android-tools d8 apksigner git wget unzip
        mkdir -p ~/.gradle
        echo "android.aapt2FromMavenOverride=${'$'}PREFIX/bin/aapt2" >> ~/.gradle/gradle.properties
        """.trimIndent()
    }

    val filePath = activeFile?.path?.takeIf { it.isNotBlank() } ?: "agent_quickstart.py"
    val fileContent = activeFile?.content ?: "print('Antigravity Termux Bridge Ready')"
    val singleRunCmd = when {
        filePath.endsWith(".py", ignoreCase = true) -> "python3 ~/antigravity/$filePath"
        filePath.endsWith(".sh", ignoreCase = true) -> "bash ~/antigravity/$filePath"
        filePath.endsWith(".html", ignoreCase = true) -> "python3 -m http.server 8080 -d ~/antigravity"
        else -> "ls -la ~/antigravity"
    }
    val singleFileScript = remember(filePath, fileContent, singleRunCmd) {
        """
        mkdir -p ~/antigravity
        cat << 'ANTIGRAVITY_EOF' > ~/antigravity/$filePath
        $fileContent
        ANTIGRAVITY_EOF
        $singleRunCmd
        """.trimIndent()
    }
    val prootSetupScript = remember {
        """
        pkg update -y && pkg install -y python proot-distro
        proot-distro install ubuntu
        proot-distro login ubuntu -- bash -lc "apt update && apt install -y python3 python3-pip && pip install google-antigravity"
        """.trimIndent()
    }

    val primaryScript = when (mode) {
        TermuxBridgeMode.CLOUD_CI_APK -> cloudCiScript
        TermuxBridgeMode.ON_DEVICE_APK -> onDeviceApkScript
        TermuxBridgeMode.SINGLE_FILE -> singleFileScript
    }
    val setupScript = when (mode) {
        TermuxBridgeMode.CLOUD_CI_APK -> cloudCiSetupCmd
        TermuxBridgeMode.ON_DEVICE_APK -> onDeviceSetupCmd
        TermuxBridgeMode.SINGLE_FILE -> prootSetupScript
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Termux Bridge (${files.size} Files)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TermuxBridgeMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = {
                                mode = option
                                copiedLabel = null
                            },
                            label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("termux_mode_${option.name.lowercase()}"),
                        )
                    }
                }

                Text(
                    text = when (mode) {
                        TermuxBridgeMode.CLOUD_CI_APK ->
                            "Recommended: Exports all ${files.size} workspace files into Termux, adds a GitHub Actions APK workflow, pushes, and downloads app-debug.apk to your phone without needing the Android SDK on-device."
                        TermuxBridgeMode.ON_DEVICE_APK ->
                            "Exports all ${files.size} workspace files and runs Gradle in Termux on-device. Requires installing Termux ARM64 toolchain + \$HOME/android-sdk once beforehand."
                        TermuxBridgeMode.SINGLE_FILE ->
                            "Exports & runs the active file ($filePath). Local web servers on port 8080 open live in /browser at http://localhost:8080."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = when (mode) {
                        TermuxBridgeMode.CLOUD_CI_APK -> "1. Export Full Workspace & Build APK via GitHub Actions"
                        TermuxBridgeMode.ON_DEVICE_APK -> "1. Export Full Workspace & Build APK Locally"
                        TermuxBridgeMode.SINGLE_FILE -> "1. Sync & Run Active File ($filePath)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = primaryScript,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(primaryScript))
                            copiedLabel = "primary"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_termux_script_button"),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (copiedLabel == "primary") "Copied" else "Copy Script")
                    }
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, primaryScript)
                                putExtra(Intent.EXTRA_SUBJECT, "Antigravity Termux Sync (${files.size} files)")
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(sendIntent, "Send script to Termux"))
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_termux_script_button"),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send")
                    }
                }

                Text(
                    text = when (mode) {
                        TermuxBridgeMode.CLOUD_CI_APK -> "2. One-Time Manual Install in Termux (Lightweight)"
                        TermuxBridgeMode.ON_DEVICE_APK -> "2. One-Time Manual Install in Termux (+ \$HOME/android-sdk)"
                        TermuxBridgeMode.SINGLE_FILE -> "2. Optional Linux glibc Runtime (proot-distro)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = setupScript,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(setupScript))
                        copiedLabel = "setup"
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copy_proot_script_button"),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copiedLabel == "setup") "Setup Command Copied" else "Copy 1-Time Termux Setup")
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onOpenBuildMonitor,
                    modifier = Modifier.testTag("dialog_open_build_monitor_button"),
                ) {
                    Text("Open Build Monitor")
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
    )
}

