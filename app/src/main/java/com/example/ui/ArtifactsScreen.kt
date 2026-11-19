package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.runtime.Composable
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
import com.example.model.ApprovalStatus
import com.example.model.Artifact
import com.example.model.ArtifactKind
import com.example.model.UiState
import com.example.ui.components.ApprovalBadge
import com.example.ui.components.SyntaxCodeViewer
import com.example.ui.components.UnifiedDiffView

@Composable
fun ArtifactsScreen(
    artifactsState: UiState<List<Artifact>>,
    selectedArtifactId: String?,
    onSelectArtifact: (String?) -> Unit,
    onSetApproval: (Artifact, ApprovalStatus, String) -> Unit,
    onPreviewHtmlInBrowser: (Artifact) -> Unit,
) {
    val artifacts = (artifactsState as? UiState.Success)?.data.orEmpty()
    val activeArtifact = artifacts.firstOrNull { it.id == selectedArtifactId }

    if (activeArtifact != null) {
        ArtifactDetailPane(
            artifact = activeArtifact,
            onBack = { onSelectArtifact(null) },
            onApprove = { note -> onSetApproval(activeArtifact, ApprovalStatus.APPROVED, note) },
            onRequestChanges = { note -> onSetApproval(activeArtifact, ApprovalStatus.CHANGES_REQUESTED, note) },
            onPreviewInBrowser = { onPreviewHtmlInBrowser(activeArtifact) },
        )
        return
    }

    var kindFilter by remember { mutableStateOf<ArtifactKind?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Text(
            text = "Artifacts & Review Pane",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Inspect agent Implementation Plans, Code Diffs, Walkthroughs, and Generative UI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = kindFilter == null,
                onClick = { kindFilter = null },
                label = { Text("All (${artifacts.size})") },
            )
            ArtifactKind.entries.forEach { kind ->
                val count = artifacts.count { ArtifactKind.safeParse(it.kind) == kind }
                FilterChip(
                    selected = kindFilter == kind,
                    onClick = { kindFilter = kind },
                    label = { Text("${kind.label} ($count)") },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when (artifactsState) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Text(artifactsState.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                val filtered = if (kindFilter == null) artifacts
                else artifacts.filter { ArtifactKind.safeParse(it.kind) == kindFilter }

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No artifacts yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Run an agent task or apply a file edit to generate reviewable artifacts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { artifact ->
                            ArtifactSummaryCard(
                                artifact = artifact,
                                onClick = { onSelectArtifact(artifact.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactSummaryCard(
    artifact: Artifact,
    onClick: () -> Unit,
) {
    val kind = ArtifactKind.safeParse(artifact.kind)
    val approval = ApprovalStatus.safeParse(artifact.approvalStatus)
    val icon = when (kind) {
        ArtifactKind.DIFF -> Icons.Rounded.Code
        ArtifactKind.HTML_PREVIEW -> Icons.Rounded.Public
        else -> Icons.Rounded.Description
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("artifact_card_${artifact.id}"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = kind.label,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = kind.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ApprovalBadge(status = approval)
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = artifact.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = artifact.content.lines().take(3).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtifactDetailPane(
    artifact: Artifact,
    onBack: () -> Unit,
    onApprove: (String) -> Unit,
    onRequestChanges: (String) -> Unit,
    onPreviewInBrowser: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val kind = ArtifactKind.safeParse(artifact.kind)
    val approval = ApprovalStatus.safeParse(artifact.approvalStatus)
    var feedbackNote by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
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
                    modifier = Modifier.testTag("artifact_back_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Artifacts")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artifact.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ApprovalBadge(status = approval)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (kind == ArtifactKind.HTML_PREVIEW) {
                Button(
                    onClick = onPreviewInBrowser,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_in_browser_button"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Live Preview in /browser")
                }
            }

            when (kind) {
                ArtifactKind.DIFF -> UnifiedDiffView(diffText = artifact.content)
                ArtifactKind.HTML_PREVIEW -> SyntaxCodeViewer(code = artifact.content, language = "html")
                else -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = artifact.content,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            // Review Feedback Controls
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Review Feedback",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackNote,
                        onValueChange = { feedbackNote = it },
                        placeholder = { Text("Optional review comment sent to agent session...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("artifact_feedback_input"),
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val note = feedbackNote
                                feedbackNote = ""
                                onRequestChanges(note)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("artifact_request_changes_button"),
                        ) {
                            Icon(Icons.Rounded.Feedback, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Request Changes")
                        }
                        Button(
                            onClick = {
                                val note = feedbackNote
                                feedbackNote = ""
                                onApprove(note)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("artifact_approve_button"),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Approve")
                        }
                    }
                }
            }
        }
    }
}
