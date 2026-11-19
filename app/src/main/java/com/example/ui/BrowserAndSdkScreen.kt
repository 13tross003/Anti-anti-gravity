package com.example.ui

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.AntigravitySeedData
import com.example.model.AgentMode
import com.example.model.AgentProfile
import com.example.model.BuiltinTool
import com.example.model.GeminiModelOption
import com.example.ui.components.SyntaxCodeViewer
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun BrowserAndSdkScreen(
    profile: AgentProfile,
    userEmail: String?,
    browserUrl: String,
    browserHtmlPreview: String?,
    browserLogs: List<String>,
    initialTab: Int = 0,
    onNavigateUrl: (String) -> Unit,
    onAppendBrowserLog: (String) -> Unit,
    onSendToAgent: () -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateMode: (AgentMode) -> Unit,
    onToggleReadOnly: (Boolean) -> Unit,
    onToggleTool: (BuiltinTool) -> Unit,
    onToggleDestructiveHooks: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onAddAllowlistDomain: (String) -> Unit,
    onRemoveAllowlistDomain: (String) -> Unit,
    onNotifyCopied: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_browser_subagent"),
            ) {
                Text("/browser Subagent")
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_sdk_config"),
            ) {
                Text("LocalAgentConfig")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            BrowserSubagentPane(
                browserUrl = browserUrl,
                browserHtmlPreview = browserHtmlPreview,
                browserLogs = browserLogs,
                allowlist = profile.browserAllowlist,
                onNavigateUrl = onNavigateUrl,
                onAppendLog = onAppendBrowserLog,
                onSendToAgent = onSendToAgent,
            )
        } else {
            SdkConfigPane(
                profile = profile,
                userEmail = userEmail,
                onUpdateModel = onUpdateModel,
                onUpdateMode = onUpdateMode,
                onToggleReadOnly = onToggleReadOnly,
                onToggleTool = onToggleTool,
                onToggleDestructiveHooks = onToggleDestructiveHooks,
                onToggleDarkTheme = onToggleDarkTheme,
                onAddAllowlistDomain = onAddAllowlistDomain,
                onRemoveAllowlistDomain = onRemoveAllowlistDomain,
                onNotifyCopied = onNotifyCopied,
                onSignOut = onSignOut,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserSubagentPane(
    browserUrl: String,
    browserHtmlPreview: String?,
    browserLogs: List<String>,
    allowlist: List<String>,
    onNavigateUrl: (String) -> Unit,
    onAppendLog: (String) -> Unit,
    onSendToAgent: () -> Unit,
) {
    var urlInput by remember(browserUrl) { mutableStateOf(browserUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("Browser Subagent URL") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("browser_url_input"),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            )
            Button(
                onClick = { onNavigateUrl(urlInput) },
                modifier = Modifier
                    .height(48.dp)
                    .testTag("browser_go_button"),
            ) {
                Text("Go")
            }
            IconButton(
                onClick = { webViewRef?.reload() },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Reload page")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (browserHtmlPreview != null) {
                    "Rendering Generative UI HTML Artifact"
                } else {
                    "Allowlist: ${allowlist.joinToString(", ")}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onSendToAgent,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("browser_send_to_agent_button"),
            ) {
                Text("Inspect in Agent", style = MaterialTheme.typography.labelSmall)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val host = request?.url?.host?.lowercase() ?: return false
                                val permitted = allowlist.any { d ->
                                    host == d.lowercase() || host.endsWith(".${d.lowercase()}")
                                }
                                if (!permitted) {
                                    onAppendLog("[ACTUATION BLOCKED] Navigation to $host denied by allowlist.")
                                    return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                onAppendLog("[LOADED] ${url ?: "local-artifact"}")
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                                if (msg != null) {
                                    onAppendLog("[CONSOLE ${msg.messageLevel()}] ${msg.message()}")
                                }
                                return true
                            }
                        }
                        webViewRef = this
                    }
                },
                update = { wv ->
                    if (browserHtmlPreview != null) {
                        wv.loadDataWithBaseURL(null, browserHtmlPreview, "text/html", "UTF-8", null)
                    } else if (wv.url != browserUrl) {
                        wv.loadUrl(browserUrl)
                    }
                },
            )
        }

        // Console & Actuation Log Drawer
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "BROWSER CONSOLE & ACTUATION LOG",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                browserLogs.forEach { log ->
                    Text(
                        text = log,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SdkConfigPane(
    profile: AgentProfile,
    userEmail: String?,
    onUpdateModel: (String) -> Unit,
    onUpdateMode: (AgentMode) -> Unit,
    onToggleReadOnly: (Boolean) -> Unit,
    onToggleTool: (BuiltinTool) -> Unit,
    onToggleDestructiveHooks: (Boolean) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onAddAllowlistDomain: (String) -> Unit,
    onRemoveAllowlistDomain: (String) -> Unit,
    onNotifyCopied: () -> Unit,
    onSignOut: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val currentMode = AgentMode.safeParse(profile.agentMode)
    var newDomain by remember { mutableStateOf("") }

    val pythonSnippet = remember(profile) {
        AntigravitySeedData.generatePythonSdkSnippet(
            modelId = profile.preferredModel,
            mode = currentMode,
            readOnly = profile.readOnlyMode,
            enabledTools = profile.enabledTools,
            blockDestructiveHooks = profile.blockDestructiveHooks,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Account Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = userEmail ?: "Google Account Connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sign Out")
                }
            }
        }

        // Model & Theme Selection
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gemini Runtime Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GeminiModelOption.entries.forEach { opt ->
                        FilterChip(
                            selected = profile.preferredModel == opt.modelId,
                            onClick = { onUpdateModel(opt.modelId) },
                            label = { Text("${opt.displayName} (${opt.badge})") },
                        )
                    }
                }

                Text("Agent Orchestration Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgentMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentMode == mode,
                            onClick = { onUpdateMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Obsidian Dark Theme (#121317)", style = MaterialTheme.typography.bodyMedium)
                            Text("Switch between Antigravity Dark and Crisp White (#FFFFFF)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = profile.darkTheme,
                        onCheckedChange = onToggleDarkTheme,
                        modifier = Modifier.testTag("dark_theme_switch"),
                    )
                }
            }
        }

        // CapabilitiesConfig & Lifecycle Hooks
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("CapabilitiesConfig & Hooks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("BuiltinTools.read_only()", style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMonoFontFamily)
                        Text("Restrict agent to non-mutating inspection tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = profile.readOnlyMode,
                        onCheckedChange = onToggleReadOnly,
                        modifier = Modifier.testTag("readonly_switch"),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("@hooks.pre_turn Safety Guard", style = MaterialTheme.typography.bodyMedium, fontFamily = JetBrainsMonoFontFamily)
                        Text("Block destructive commands like rm -rf before execution", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = profile.blockDestructiveHooks,
                        onCheckedChange = onToggleDestructiveHooks,
                        modifier = Modifier.testTag("pre_turn_hook_switch"),
                    )
                }

                Text("Enabled BuiltinTools", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BuiltinTool.entries.forEach { tool ->
                        val active = if (profile.readOnlyMode) tool.readOnlySafe else profile.enabledTools.contains(tool.name)
                        FilterChip(
                            selected = active,
                            onClick = { if (!profile.readOnlyMode) onToggleTool(tool) },
                            enabled = !profile.readOnlyMode,
                            label = { Text(tool.name, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.5.sp) },
                        )
                    }
                }
            }
        }

        // Browser Actuation Allowlist
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Browser Actuation Allowlist", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.browserAllowlist.forEach { domain ->
                        InputChip(
                            selected = true,
                            onClick = { onRemoveAllowlistDomain(domain) },
                            label = { Text("$domain ×", fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp) },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        placeholder = { Text("Add domain (e.g. github.com)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = {
                            if (newDomain.isNotBlank()) {
                                onAddAllowlistDomain(newDomain.trim().lowercase())
                                newDomain = ""
                            }
                        },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add domain", modifier = Modifier.size(16.dp))
                        Text("Add")
                    }
                }
            }
        }

        // Exportable google-antigravity Python SDK Config
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Live google-antigravity SDK Export",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(pythonSnippet))
                    onNotifyCopied()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("copy_sdk_snippet_button"),
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy Python")
            }
        }

        SyntaxCodeViewer(code = pythonSnippet, language = "python")
        Spacer(Modifier.height(24.dp))
    }
}
