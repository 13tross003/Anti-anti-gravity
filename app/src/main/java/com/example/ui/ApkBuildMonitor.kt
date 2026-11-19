package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApkBuildMonitorState
import com.example.model.ApkBuildPhase
import com.example.model.ApkBuildSource
import com.example.model.ApkBuildStep
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAntigravityColors
import kotlin.math.roundToInt

@Composable
fun ApkBuildTopBarPill(
    state: ApkBuildMonitorState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalAntigravityColors.current
    val (accent, label) = when (state.phase) {
        ApkBuildPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant to "APK Build"
        ApkBuildPhase.QUEUED -> ext.statusReview to "Queued"
        ApkBuildPhase.COMPILING -> ext.statusRunning to "APK ${(state.progress * 100).roundToInt()}%"
        ApkBuildPhase.SUCCEEDED -> ext.statusCompleted to "APK Ready"
        ApkBuildPhase.FAILED -> ext.statusError to "Build Fail"
        ApkBuildPhase.DISCONNECTED -> ext.statusReview to "Offline"
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .testTag("apk_build_topbar_pill"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = 1.dp,
                color = if (state.phase == ApkBuildPhase.IDLE) MaterialTheme.colorScheme.outlineVariant else accent.copy(alpha = 0.6f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.phase == ApkBuildPhase.COMPILING || state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = accent,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkBuildMonitorSheet(
    state: ApkBuildMonitorState,
    onDismiss: () -> Unit,
    onSelectSource: (ApkBuildSource) -> Unit,
    onUpdateRepo: (String) -> Unit,
    onUpdateToken: (String) -> Unit,
    onUpdateLocalUrl: (String) -> Unit,
    onRefreshOnce: () -> Unit,
    onTogglePolling: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = LocalAntigravityColors.current.statusCompleted,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = "APK Build Monitor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Real-time compilation & artifact telemetry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_build_monitor_button"),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close APK Build Monitor")
                }
            }

            ApkBuildMonitorContent(
                state = state,
                onSelectSource = onSelectSource,
                onUpdateRepo = onUpdateRepo,
                onUpdateToken = onUpdateToken,
                onUpdateLocalUrl = onUpdateLocalUrl,
                onRefreshOnce = onRefreshOnce,
                onTogglePolling = onTogglePolling,
            )
        }
    }
}

@Composable
fun ApkBuildMonitorContent(
    state: ApkBuildMonitorState,
    onSelectSource: (ApkBuildSource) -> Unit,
    onUpdateRepo: (String) -> Unit,
    onUpdateToken: (String) -> Unit,
    onUpdateLocalUrl: (String) -> Unit,
    onRefreshOnce: () -> Unit,
    onTogglePolling: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ext = LocalAntigravityColors.current
    var showToken by remember { mutableStateOf(false) }

    val statusColor: Color = when (state.phase) {
        ApkBuildPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        ApkBuildPhase.QUEUED -> ext.statusReview
        ApkBuildPhase.COMPILING -> ext.statusRunning
        ApkBuildPhase.SUCCEEDED -> ext.statusCompleted
        ApkBuildPhase.FAILED -> ext.statusError
        ApkBuildPhase.DISCONNECTED -> ext.statusReview
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        label = "apk_build_progress",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Source Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ApkBuildSource.entries.forEach { src ->
                FilterChip(
                    selected = state.source == src,
                    onClick = { onSelectSource(src) },
                    label = { Text(src.label) },
                    modifier = Modifier.testTag("build_source_${src.name.lowercase()}"),
                )
            }
        }

        // Connection Configuration
        if (state.source == ApkBuildSource.GITHUB_ACTIONS) {
            OutlinedTextField(
                value = state.githubRepo,
                onValueChange = onUpdateRepo,
                label = { Text("GitHub Repository (owner/repo)") },
                placeholder = { Text("e.g. myuser/antigravity-app") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("build_github_repo_input"),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            )
            OutlinedTextField(
                value = state.githubToken,
                onValueChange = onUpdateToken,
                label = { Text("GitHub Token (Optional, private repos only)") },
                placeholder = { Text("ghp_... (kept in memory only)") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showToken = !showToken }) {
                        Icon(
                            imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (showToken) "Hide token" else "Show token",
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("build_github_token_input"),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            )
        } else {
            OutlinedTextField(
                value = state.localStatusUrl,
                onValueChange = onUpdateLocalUrl,
                label = { Text("Termux Local Telemetry URL") },
                placeholder = { Text("http://127.0.0.1:8765/build-status.json") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("build_local_url_input"),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            )
        }

        // Monitor Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onTogglePolling,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("toggle_build_polling_button"),
            ) {
                Icon(
                    imageVector = if (state.isPolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.isPolling) "Pause Live Monitor" else "Start Live Monitor")
            }
            OutlinedButton(
                onClick = onRefreshOnce,
                enabled = !state.isRefreshing,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("refresh_build_status_button"),
            ) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh build status", modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("Check")
            }
        }

        // Live Progress & Build State Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apk_build_state_card"),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = state.phase.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFontFamily,
                        )
                        if (state.isPolling) {
                            Text(
                                text = "• LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.statusRunning,
                                fontFamily = JetBrainsMonoFontFamily,
                            )
                        }
                    }
                    Text(
                        text = "${(animatedProgress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = statusColor,
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("apk_build_progress_bar"),
                )

                Text(
                    text = state.stageLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                if (state.workflowName.isNotBlank() || state.lastCheckedAt.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val meta = buildString {
                            if (state.workflowName.isNotBlank()) append(state.workflowName)
                            if (state.runNumber != null) append(" #${state.runNumber}")
                            if (state.branch.isNotBlank()) append(" (${state.branch})")
                            if (state.commitSha.isNotBlank()) append(" @ ${state.commitSha}")
                        }
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.lastCheckedAt.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Checked ${state.lastCheckedAt}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Error / Connection Notice
        AnimatedVisibility(visible = !state.errorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, ext.statusError.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = ext.statusError,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = state.errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Real Job / Stage Steps
        if (state.steps.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Compilation Pipeline Steps (${state.steps.count { it.status == "completed" }}/${state.steps.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.steps.forEach { step ->
                        ApkBuildStepRow(step = step)
                    }
                }
            }
        }

        // Compiled APK Artifacts & Download Action
        if (state.artifacts.isNotEmpty() || state.runHtmlUrl.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, ext.statusCompleted.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.artifacts.forEach { art ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    tint = ext.statusCompleted,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column {
                                    Text(
                                        text = art.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = JetBrainsMonoFontFamily,
                                    )
                                    Text(
                                        text = formatByteSize(art.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (state.runHtmlUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.runHtmlUrl))
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_apk_download_url_button"),
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.phase == ApkBuildPhase.SUCCEEDED) "Download APK on GitHub Actions"
                                else "Open Live Workflow Run"
                            )
                        }
                    }
                }
            }
        }

        // Real Log Tail (from Termux local Gradle builds)
        if (state.logTail.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ext.codeBackground,
                border = BorderStroke(1.dp, ext.codeBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "GRADLE OUTPUT TAIL",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = state.logTail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkBuildStepRow(step: ApkBuildStep) {
    val ext = LocalAntigravityColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            step.status == "completed" && step.conclusion == "success" -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Completed",
                    tint = ext.statusCompleted,
                    modifier = Modifier.size(16.dp),
                )
            }
            step.status == "completed" && (step.conclusion == "failure" || step.conclusion == "cancelled") -> {
                Icon(
                    imageVector = Icons.Rounded.Cancel,
                    contentDescription = "Failed",
                    tint = ext.statusError,
                    modifier = Modifier.size(16.dp),
                )
            }
            step.status == "in_progress" -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = ext.statusRunning,
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.HourglassTop,
                    contentDescription = "Queued",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Text(
            text = "${step.number}. ${step.name}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = JetBrainsMonoFontFamily,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = step.conclusion ?: step.status,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFontFamily,
            color = when {
                step.conclusion == "success" -> ext.statusCompleted
                step.conclusion == "failure" -> ext.statusError
                step.status == "in_progress" -> ext.statusRunning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "Size available on runner"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) String.format(java.util.Locale.US, "%.2f MB", mb)
    else String.format(java.util.Locale.US, "%.0f KB", kb)
}
