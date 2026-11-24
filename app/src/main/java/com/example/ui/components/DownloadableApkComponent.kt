package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAntigravityColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data object CheckingInBrowser : ApkDownloadState
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ApkDownloadState
    data class DownloadedReady(
        val file: File,
        val sizeLabel: String,
        val awaitingInstallPermission: Boolean = false,
        val installerLaunched: Boolean = false,
    ) : ApkDownloadState
    data class OpenedInBrowser(val url: String, val sizeLabel: String) : ApkDownloadState
    data class Failed(val message: String, val candidateUrl: String?) : ApkDownloadState
}

@Composable
fun DownloadableApkComponent(
    initialSourceInput: String = "",
    onSourceInputChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = LocalAntigravityColors.current

    var sourceInput by remember(initialSourceInput) { mutableStateOf(initialSourceInput) }
    var showSourceEditor by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf<ApkDownloadState>(ApkDownloadState.Idle) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val resolvedUrl = remember(sourceInput) { resolveApkDownloadUrl(sourceInput) }

    fun launchSystemApkInstaller(apkFile: File, sizeLabel: String) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            downloadState = ApkDownloadState.Failed(
                message = "Downloaded APK file is missing. Please download it again.",
                candidateUrl = resolvedUrl,
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            downloadState = ApkDownloadState.DownloadedReady(
                file = apkFile,
                sizeLabel = sizeLabel,
                awaitingInstallPermission = true,
                installerLaunched = false,
            )
            return
        }

        runCatching {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }.onSuccess {
            downloadState = ApkDownloadState.DownloadedReady(
                file = apkFile,
                sizeLabel = sizeLabel,
                awaitingInstallPermission = false,
                installerLaunched = true,
            )
        }.onFailure { err ->
            downloadState = ApkDownloadState.Failed(
                message = "Could not open Android package installer: ${err.localizedMessage}",
                candidateUrl = resolvedUrl,
            )
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        val current = downloadState as? ApkDownloadState.DownloadedReady ?: return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        ) {
            launchSystemApkInstaller(current.file, current.sizeLabel)
        }
    }

    fun requestUnknownSourcesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                )
                unknownSourcesLauncher.launch(intent)
            }.onFailure { err ->
                downloadState = ApkDownloadState.Failed(
                    message = "Unable to open install permission settings: ${err.localizedMessage}",
                    candidateUrl = resolvedUrl,
                )
            }
        }
    }

    fun launchBrowserDownload(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }.onFailure { err ->
            downloadState = ApkDownloadState.Failed(
                message = "Could not open browser for download: ${err.localizedMessage}",
                candidateUrl = url,
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("downloadable_apk_component"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, ext.statusCompleted.copy(alpha = 0.5f)),
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ext.statusCompleted.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = "Download Signed APK",
                        tint = ext.statusCompleted,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Signed Android Installer (.apk)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = resolvedUrl
                            ?: "Set your GitHub repo (owner/repo) or direct .apk URL to download & install.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (resolvedUrl != null) JetBrainsMonoFontFamily else MaterialTheme.typography.bodySmall.fontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            val isBusy = downloadState is ApkDownloadState.Downloading ||
                downloadState is ApkDownloadState.CheckingInBrowser

            Button(
                onClick = {
                    val target = resolvedUrl
                    if (target == null) {
                        showSourceEditor = true
                        downloadState = ApkDownloadState.Failed(
                            message = "Enter your GitHub repo (owner/repo) from the build-apk.yml push, or a direct https://.../*.apk URL first.",
                            candidateUrl = null,
                        )
                        return@Button
                    }
                    activeJob?.cancel()
                    activeJob = scope.launch {
                        downloadState = ApkDownloadState.Downloading(0L, -1L)
                        val result = downloadSignedApkToCache(
                            context = context,
                            urlString = target,
                            onProgress = { downloaded, total ->
                                downloadState = ApkDownloadState.Downloading(downloaded, total)
                            },
                        )
                        when (result) {
                            is ApkDownloadResult.Success -> {
                                launchSystemApkInstaller(result.file, result.sizeLabel)
                            }
                            is ApkDownloadResult.Error -> {
                                downloadState = ApkDownloadState.Failed(
                                    message = result.message,
                                    candidateUrl = target,
                                )
                            }
                        }
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ext.statusCompleted,
                    contentColor = MaterialTheme.colorScheme.surface,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("download_signed_apk_button"),
            ) {
                when (val state = downloadState) {
                    is ApkDownloadState.Downloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                        )
                        Spacer(Modifier.width(8.dp))
                        val label = if (state.totalBytes > 0L) {
                            val pct = ((state.bytesDownloaded * 100) / state.totalBytes).coerceIn(0L, 100L)
                            "Downloading APK ($pct%)..."
                        } else {
                            "Downloading APK (${formatBytesMb(state.bytesDownloaded)})..."
                        }
                        Text(label, fontWeight = FontWeight.SemiBold)
                    }
                    is ApkDownloadState.CheckingInBrowser -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Verifying APK...", fontWeight = FontWeight.SemiBold)
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Download Signed APK", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        val target = resolvedUrl
                        if (target == null) {
                            showSourceEditor = true
                            downloadState = ApkDownloadState.Failed(
                                message = "Enter your GitHub repo (owner/repo) or direct .apk URL first.",
                                candidateUrl = null,
                            )
                            return@OutlinedButton
                        }
                        activeJob?.cancel()
                        activeJob = scope.launch {
                            downloadState = ApkDownloadState.CheckingInBrowser
                            val preflight = verifyApkEndpoint(target)
                            when (preflight) {
                                is ApkPreflightResult.Verified -> {
                                    downloadState = ApkDownloadState.OpenedInBrowser(preflight.url, preflight.sizeLabel)
                                    launchBrowserDownload(preflight.url)
                                }
                                is ApkPreflightResult.Error -> {
                                    downloadState = ApkDownloadState.Failed(preflight.message, target)
                                }
                            }
                        }
                    },
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_apk_browser_button"),
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("In Browser")
                }

                OutlinedButton(
                    onClick = { showSourceEditor = !showSourceEditor },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("configure_apk_source_button"),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Configure APK source", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showSourceEditor) "Done" else "Source")
                }
            }

            AnimatedVisibility(visible = downloadState is ApkDownloadState.Downloading) {
                val downloading = downloadState as? ApkDownloadState.Downloading
                if (downloading != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (downloading.totalBytes > 0L) {
                            val fraction = (downloading.bytesDownloaded.toFloat() / downloading.totalBytes.toFloat())
                                .coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                            Text(
                                text = "${formatBytesMb(downloading.bytesDownloaded)} / ${formatBytesMb(downloading.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = downloadState is ApkDownloadState.DownloadedReady) {
                val ready = downloadState as? ApkDownloadState.DownloadedReady
                if (ready != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ext.statusCompleted.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, ext.statusCompleted.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (ready.awaitingInstallPermission) {
                                        Icons.Rounded.Security
                                    } else {
                                        Icons.Rounded.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = ext.statusCompleted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = when {
                                        ready.awaitingInstallPermission ->
                                            "APK verified (${ready.sizeLabel}). Grant \"Install unknown apps\" permission to install."
                                        ready.installerLaunched ->
                                            "Verified signed APK (${ready.sizeLabel}) • Android installer launched."
                                        else ->
                                            "APK verified and saved (${ready.sizeLabel}). Ready to install."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            if (ready.awaitingInstallPermission) {
                                Button(
                                    onClick = { requestUnknownSourcesPermission() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("grant_apk_install_permission_button"),
                                ) {
                                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Allow App Installs in Settings")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { launchSystemApkInstaller(ready.file, ready.sizeLabel) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("install_downloaded_apk_button"),
                                ) {
                                    Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Install Signed APK")
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = downloadState is ApkDownloadState.OpenedInBrowser) {
                val ok = downloadState as? ApkDownloadState.OpenedInBrowser
                if (ok != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Verified APK",
                            tint = ext.statusCompleted,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Verified signed APK (${ok.sizeLabel}) • Opened in browser downloader",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.statusCompleted,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = downloadState is ApkDownloadState.Failed) {
                val fail = downloadState as? ApkDownloadState.Failed
                if (fail != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, ext.statusError.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = ext.statusError,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = fail.message,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (fail.candidateUrl != null) {
                                TextButton(
                                    onClick = { launchBrowserDownload(fail.candidateUrl) },
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Text("Open Link in Browser Anyway")
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showSourceEditor) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = sourceInput,
                        onValueChange = {
                            sourceInput = it
                            downloadState = ApkDownloadState.Idle
                            onSourceInputChanged(it)
                        },
                        label = { Text("GitHub Repo (owner/repo) or Direct APK URL") },
                        placeholder = { Text("e.g. myuser/antigravity-app or https://.../app-debug.apk") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_source_input"),
                    )
                    Text(
                        text = "Tip: Entering owner/repo automatically targets your GitHub Release built by .github/workflows/build-apk.yml.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun resolveApkDownloadUrl(rawInput: String): String? {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) return null

    if (!trimmed.contains("://") && trimmed.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$"))) {
        return "https://github.com/$trimmed/releases/latest/download/app-debug.apk"
    }

    val isLocalNoScheme = !trimmed.startsWith("http://") && !trimmed.startsWith("https://") &&
        (trimmed.startsWith("localhost", ignoreCase = true) || trimmed.startsWith("127.0.0.1"))
    val withScheme = when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        isLocalNoScheme -> "http://$trimmed"
        else -> "https://$trimmed"
    }

    val ghMatch = Regex("^https?://github\\.com/([^/]+)/([^/]+)/?$", RegexOption.IGNORE_CASE).matchEntire(withScheme)
    if (ghMatch != null) {
        val owner = ghMatch.groupValues[1]
        val repo = ghMatch.groupValues[2]
        return "https://github.com/$owner/$repo/releases/latest/download/app-debug.apk"
    }

    return withScheme
}

private sealed interface ApkPreflightResult {
    data class Verified(val url: String, val sizeLabel: String) : ApkPreflightResult
    data class Error(val message: String) : ApkPreflightResult
}

private sealed interface ApkDownloadResult {
    data class Success(val file: File, val sizeLabel: String) : ApkDownloadResult
    data class Error(val message: String) : ApkDownloadResult
}

private suspend fun openFollowedConnection(
    urlString: String,
    rangeHeader: String? = null,
): HttpURLConnection {
    var currentUrl = urlString
    var conn: HttpURLConnection? = null
    for (hop in 0 until 6) {
        conn?.disconnect()
        conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 20_000
            if (rangeHeader != null) {
                setRequestProperty("Range", rangeHeader)
            }
            setRequestProperty("User-Agent", "Google-Antigravity-Android/1.0")
        }
        val code = conn.responseCode
        if (code in 300..399) {
            val location = conn.getHeaderField("Location")
            if (location.isNullOrBlank()) return conn
            currentUrl = URL(URL(currentUrl), location).toString()
        } else {
            return conn
        }
    }
    return conn ?: throw IllegalStateException("Too many redirects for $urlString")
}

private suspend fun verifyApkEndpoint(urlString: String): ApkPreflightResult =
    withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = openFollowedConnection(urlString, rangeHeader = "bytes=0-3")
            val code = conn.responseCode
            if (code == 404) {
                return@withContext ApkPreflightResult.Error(
                    "No APK release found yet at $urlString (HTTP 404). Push your project to GitHub first so .github/workflows/build-apk.yml publishes app-debug.apk.",
                )
            }
            if (code !in 200..299) {
                return@withContext ApkPreflightResult.Error(
                    "Server returned HTTP $code for $urlString.",
                )
            }

            val header = ByteArray(4)
            val bytesRead = conn.inputStream.use { it.read(header) }
            if (!isPkZipHeader(header, bytesRead)) {
                return@withContext ApkPreflightResult.Error(
                    "URL returned HTTP $code, but it is not a valid APK binary (expected a .apk file rather than an HTML page).",
                )
            }

            val totalBytes = parseContentLength(conn)
            val sizeLabel = if (totalBytes > 0L) formatBytesMb(totalBytes) else "Valid APK"
            ApkPreflightResult.Verified(url = urlString, sizeLabel = sizeLabel)
        } catch (e: Exception) {
            ApkPreflightResult.Error("Could not reach APK host: ${e.localizedMessage ?: "network error"}")
        } finally {
            conn?.disconnect()
        }
    }

private suspend fun downloadSignedApkToCache(
    context: Context,
    urlString: String,
    onProgress: suspend (Long, Long) -> Unit,
): ApkDownloadResult = withContext(Dispatchers.IO) {
    val downloadsDir = File(context.cacheDir, "apk_downloads").apply { mkdirs() }
    val partFile = File(downloadsDir, "antigravity-signed.apk.part")
    val finalFile = File(downloadsDir, "antigravity-signed.apk")
    var conn: HttpURLConnection? = null
    try {
        if (partFile.exists()) partFile.delete()

        conn = openFollowedConnection(urlString)
        val code = conn.responseCode
        if (code == 404) {
            return@withContext ApkDownloadResult.Error(
                "No APK release found yet at $urlString (HTTP 404). Push your project to GitHub first so .github/workflows/build-apk.yml publishes app-debug.apk.",
            )
        }
        if (code !in 200..299) {
            return@withContext ApkDownloadResult.Error("Server returned HTTP $code for $urlString.")
        }

        val totalBytes = conn.contentLengthLong
        var downloaded = 0L
        var lastUiUpdate = 0L
        val buffer = ByteArray(16 * 1024)
        val headerProbe = ByteArray(4)
        var headerProbeLen = 0
        var verifiedHeader = false

        conn.inputStream.use { input ->
            FileOutputStream(partFile).use { output ->
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (!verifiedHeader) {
                        val needed = (4 - headerProbeLen).coerceAtMost(read)
                        System.arraycopy(buffer, 0, headerProbe, headerProbeLen, needed)
                        headerProbeLen += needed
                        if (headerProbeLen == 4) {
                            if (!isPkZipHeader(headerProbe, 4)) {
                                partFile.delete()
                                return@withContext ApkDownloadResult.Error(
                                    "URL did not return a valid APK binary (expected a .apk file rather than an HTML page).",
                                )
                            }
                            verifiedHeader = true
                        }
                    }
                    output.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate >= 120L) {
                        lastUiUpdate = now
                        withContext(Dispatchers.Main) {
                            onProgress(downloaded, totalBytes)
                        }
                    }
                }
                output.fd.sync()
            }
        }

        if (!verifiedHeader || downloaded == 0L) {
            partFile.delete()
            return@withContext ApkDownloadResult.Error("Downloaded file was empty or not a valid APK.")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }

        ApkDownloadResult.Success(file = finalFile, sizeLabel = formatBytesMb(downloaded))
    } catch (e: Exception) {
        partFile.delete()
        ApkDownloadResult.Error("APK download failed: ${e.localizedMessage ?: "network error"}")
    } finally {
        conn?.disconnect()
    }
}

private fun isPkZipHeader(bytes: ByteArray, length: Int): Boolean =
    length >= 4 &&
        bytes[0] == 0x50.toByte() &&
        bytes[1] == 0x4B.toByte() &&
        bytes[2] == 0x03.toByte() &&
        bytes[3] == 0x04.toByte()

private fun parseContentLength(conn: HttpURLConnection): Long {
    val contentRange = conn.getHeaderField("Content-Range")
    if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
        contentRange.substringAfterLast("/").trim().toLongOrNull()?.let { return it }
    }
    return conn.contentLengthLong
}

private fun formatBytesMb(bytes: Long): String =
    String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
