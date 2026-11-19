package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.onGoogleSignInClicked
import com.example.ui.components.AntigravityLogoMark
import com.example.ui.theme.AntigravityObsidian
import com.example.ui.theme.AntigravitySurfaceElevated
import com.example.ui.theme.GoogleBlueDark
import com.example.ui.theme.GoogleGreenDark
import com.example.ui.theme.GoogleRedDark
import com.example.ui.theme.GoogleYellowDark

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AntigravityObsidian)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AntigravityLogoMark(size = 36.dp)
                    Column {
                        Text(
                            text = "Google Antigravity",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Agent-First Development Platform",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoogleBlueDark,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(186.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color(0xFF282C37), RoundedCornerShape(18.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.antigravity_hero_1794929097769),
                        contentDescription = "Google Antigravity orbital agent workspace illustration",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, AntigravityObsidian.copy(alpha = 0.86f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Liftoff for Android",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Orchestrate autonomous coding agents, inspect artifacts, edit files, and preview /browser output.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBDC1C6),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                SurfaceCardFeature(
                    icon = Icons.Rounded.AutoAwesome,
                    accent = GoogleBlueDark,
                    title = "Agent Manager & Mission Control",
                    subtitle = "Dispatch parallel agents across workspaces with Planning & Fast execution modes.",
                )
                Spacer(Modifier.height(10.dp))
                SurfaceCardFeature(
                    icon = Icons.Rounded.Code,
                    accent = GoogleGreenDark,
                    title = "Code Editor & Terminal",
                    subtitle = "JetBrains Mono syntax viewer, inline Gemini code edits, and @hooks.pre_turn safety checks.",
                )
                Spacer(Modifier.height(10.dp))
                SurfaceCardFeature(
                    icon = Icons.Rounded.Layers,
                    accent = GoogleYellowDark,
                    title = "Artifacts & Unified Diffs",
                    subtitle = "Review Implementation Plans, Task Lists, Diffs, and Walkthroughs before applying.",
                )
                Spacer(Modifier.height(10.dp))
                SurfaceCardFeature(
                    icon = Icons.Rounded.Public,
                    accent = GoogleRedDark,
                    title = "Browser Subagent & SDK Config",
                    subtitle = "Live /browser artifact previews and live google-antigravity LocalAgentConfig export.",
                )
                Spacer(Modifier.height(10.dp))
                DownloadApkStarterCard(
                    onOpenUrl = { url ->
                        val clean = url.trim().let {
                            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
                        }
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(clean)))
                        }.onFailure {
                            errorMessage = "Could not open download link: ${it.localizedMessage}"
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF441816),
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = GoogleRedDark,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            },
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = AntigravityObsidian,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = AntigravityObsidian,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Connecting Google Account...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(
                            text = "Sign in with Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Workspaces, agent sessions, and artifacts sync securely via Firebase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0A6),
                )
            }
        }
    }
}

@Composable
private fun SurfaceCardFeature(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AntigravitySurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282C37)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9AA0A6),
                )
            }
        }
    }
}

@Composable
private fun DownloadApkStarterCard(
    onOpenUrl: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var releaseUrl by remember { mutableStateOf("https://github.com") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("startup_apk_download_card"),
        shape = RoundedCornerShape(14.dp),
        color = AntigravitySurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, GoogleGreenDark.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoogleGreenDark.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = "Signed Android APK Release",
                        tint = GoogleGreenDark,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Android APK Release Download",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Open your signed APK release URL in the browser (zero-permission download).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9AA0A6),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onOpenUrl(releaseUrl) },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoogleGreenDark.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("startup_download_apk_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = GoogleGreenDark,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Download APK in Browser",
                        color = GoogleGreenDark,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("startup_edit_apk_url_button"),
                ) {
                    Text(
                        text = if (expanded) "Hide URL" else "Set URL",
                        color = GoogleBlueDark,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                OutlinedTextField(
                    value = releaseUrl,
                    onValueChange = { releaseUrl = it },
                    label = { Text("Signed APK / GitHub Release URL") },
                    placeholder = { Text("https://github.com/owner/repo/releases/latest") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoogleGreenDark,
                        unfocusedBorderColor = Color(0xFF3A3F4B),
                        focusedLabelColor = GoogleGreenDark,
                        unfocusedLabelColor = Color(0xFF9AA0A6),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("startup_apk_url_input"),
                )
            }
        }
    }
}
