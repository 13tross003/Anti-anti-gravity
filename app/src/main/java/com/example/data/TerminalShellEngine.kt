package com.example.data

import com.example.model.WorkspaceFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

enum class TerminalTargetMode(val label: String) {
    ANDROID_SANDBOX("Android Shell"),
    TERMUX_BRIDGE("Termux Live"),
}

enum class TerminalStream {
    COMMAND,
    STDOUT,
    STDERR,
    SYSTEM,
}

data class TerminalLine(
    val id: String,
    val text: String,
    val stream: TerminalStream,
)

data class TerminalUiState(
    val mode: TerminalTargetMode = TerminalTargetMode.ANDROID_SANDBOX,
    val isRunning: Boolean = false,
    val currentRelativeDir: String = ".",
    val termuxCwd: String = "~/antigravity",
    val lastExitCode: Int? = null,
    val termuxBridgeUrl: String = "http://127.0.0.1:8766",
    val termuxBridgeToken: String = TerminalShellEngine.generateSessionToken(),
    val termuxConnected: Boolean = false,
    val commandHistory: List<String> = emptyList(),
    val lines: List<TerminalLine> = listOf(
        TerminalLine(
            id = "boot_0",
            text = "Google Antigravity Terminal Emulator • /system/bin/sh + Termux Live Bridge",
            stream = TerminalStream.SYSTEM,
        ),
        TerminalLine(
            id = "boot_1",
            text = "Type 'help' for built-in shortcuts, or run real shell commands (ls -la, pwd, grep, cat, find, env).",
            stream = TerminalStream.SYSTEM,
        ),
    ),
)

data class ShellResult(
    val exitCode: Int,
    val newRelativeDir: String,
    val outputLines: List<Pair<String, TerminalStream>> = emptyList(),
    val termuxCwd: String? = null,
)

object TerminalShellEngine {

    fun generateSessionToken(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sanitizeWorkspaceId(workspaceId: String): String =
        workspaceId.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "default" }

    suspend fun syncWorkspaceToDisk(
        workspaceRootDir: File,
        workspaceId: String,
        files: List<WorkspaceFile>,
    ): File = withContext(Dispatchers.IO) {
        val wsDir = File(workspaceRootDir, sanitizeWorkspaceId(workspaceId))
        if (!wsDir.exists()) wsDir.mkdirs()
        val canonicalRoot = wsDir.canonicalFile

        files.forEachIndexed { idx, wf ->
            val cleanSegments = wf.path
                .replace("\\", "/")
                .split("/")
                .filter { it.isNotBlank() && it != "." && it != ".." }
            val relPath = if (cleanSegments.isEmpty()) "file_$idx.txt" else cleanSegments.joinToString("/")
            val target = File(canonicalRoot, relPath).canonicalFile
            if (target.path.startsWith(canonicalRoot.path + File.separator) || target.path == canonicalRoot.path) {
                target.parentFile?.mkdirs()
                target.writeText(wf.content)
            }
        }
        canonicalRoot
    }

    suspend fun executeSandboxShell(
        workspaceDir: File,
        currentRelativeDir: String,
        rawCommand: String,
        onStreamLine: suspend (String, TerminalStream) -> Unit,
    ): ShellResult = withContext(Dispatchers.IO) {
        if (!workspaceDir.exists()) workspaceDir.mkdirs()
        val canonicalRoot = workspaceDir.canonicalFile
        val currentDir = resolveSafeSubdir(canonicalRoot, currentRelativeDir)

        val trimmed = rawCommand.trim()
        if (trimmed == "pwd") {
            onStreamLine(currentDir.absolutePath, TerminalStream.STDOUT)
            return@withContext ShellResult(exitCode = 0, newRelativeDir = relativePathOf(canonicalRoot, currentDir))
        }

        if (trimmed == "cd" || trimmed.startsWith("cd ")) {
            val arg = trimmed.removePrefix("cd").trim()
            val candidate = when {
                arg.isBlank() || arg == "~" -> canonicalRoot
                arg.startsWith("/") -> File(arg).canonicalFile
                else -> File(currentDir, arg).canonicalFile
            }
            return@withContext if (!candidate.exists() || !candidate.isDirectory) {
                onStreamLine("cd: $arg: No such directory", TerminalStream.STDERR)
                ShellResult(exitCode = 1, newRelativeDir = relativePathOf(canonicalRoot, currentDir))
            } else if (!candidate.path.startsWith(canonicalRoot.path)) {
                onStreamLine("cd: restricted to workspace root (${canonicalRoot.name})", TerminalStream.STDERR)
                ShellResult(exitCode = 1, newRelativeDir = relativePathOf(canonicalRoot, currentDir))
            } else {
                val rel = relativePathOf(canonicalRoot, candidate)
                onStreamLine(candidate.absolutePath, TerminalStream.SYSTEM)
                ShellResult(exitCode = 0, newRelativeDir = rel)
            }
        }

        var process: Process? = null
        try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed)
                .directory(currentDir)
            pb.environment()["HOME"] = canonicalRoot.absolutePath
            pb.environment()["PWD"] = currentDir.absolutePath
            pb.environment()["TERM"] = "xterm-256color"

            process = pb.start()
            val proc = process

            val exitCode = withTimeoutOrNull(30_000L) {
                coroutineScope {
                    val stdoutJob = async {
                        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                line?.let { onStreamLine(it, TerminalStream.STDOUT) }
                            }
                        }
                    }
                    val stderrJob = async {
                        BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                line?.let { onStreamLine(it, TerminalStream.STDERR) }
                            }
                        }
                    }
                    stdoutJob.await()
                    stderrJob.await()
                    proc.waitFor()
                }
            }

            if (exitCode == null) {
                proc.destroyForcibly()
                onStreamLine("Process timed out after 30s and was terminated.", TerminalStream.STDERR)
                ShellResult(exitCode = 124, newRelativeDir = relativePathOf(canonicalRoot, currentDir))
            } else {
                ShellResult(exitCode = exitCode, newRelativeDir = relativePathOf(canonicalRoot, currentDir))
            }
        } catch (e: Exception) {
            onStreamLine(e.localizedMessage ?: "Failed to execute shell command.", TerminalStream.STDERR)
            ShellResult(exitCode = 1, newRelativeDir = relativePathOf(canonicalRoot, currentDir))
        } finally {
            process?.let {
                if (it.isAlive) {
                    runCatching { it.destroyForcibly() }
                    runCatching { it.waitFor(200, TimeUnit.MILLISECONDS) }
                }
            }
        }
    }

    suspend fun pingTermuxBridge(bridgeUrl: String, token: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val base = bridgeUrl.trim().trimEnd('/')
            try {
                val conn = (URL("$base/health").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("X-Antigravity-Token", token)
                }
                try {
                    val code = conn.responseCode
                    val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.use { BufferedReader(InputStreamReader(it)).readText() }
                        .orEmpty()
                    if (code in 200..299) {
                        val cwd = runCatching { JSONObject(body).optString("cwd", "~/antigravity") }
                            .getOrDefault("~/antigravity")
                        true to "Connected to Termux Live Bridge ($cwd)"
                    } else {
                        false to "Bridge responded with HTTP $code: $body"
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                false to "Bridge not reachable at $base (${e.localizedMessage ?: "connection refused"})."
            }
        }

    suspend fun syncFilesToTermuxBridge(
        bridgeUrl: String,
        token: String,
        workspaceId: String,
        files: List<WorkspaceFile>,
    ): ShellResult = withContext(Dispatchers.IO) {
        val base = bridgeUrl.trim().trimEnd('/')
        try {
            val arr = JSONArray()
            files.forEach { wf ->
                arr.put(
                    JSONObject().apply {
                        put("path", wf.path)
                        put("content", wf.content)
                    }
                )
            }
            val payload = JSONObject().apply {
                put("workspace", sanitizeWorkspaceId(workspaceId))
                put("files", arr)
            }.toString()

            val (code, body) = httpPostJson("$base/sync", token, payload, timeoutMs = 15_000)
            if (code in 200..299) {
                val json = JSONObject(body)
                val cwd = json.optString("cwd", "~/antigravity")
                val count = json.optInt("count", files.size)
                ShellResult(
                    exitCode = 0,
                    newRelativeDir = ".",
                    outputLines = listOf("Synced $count workspace files to Termux ($cwd)" to TerminalStream.SYSTEM),
                    termuxCwd = cwd,
                )
            } else {
                ShellResult(
                    exitCode = 1,
                    newRelativeDir = ".",
                    outputLines = listOf("Termux sync HTTP $code: $body" to TerminalStream.STDERR),
                )
            }
        } catch (e: Exception) {
            ShellResult(
                exitCode = 1,
                newRelativeDir = ".",
                outputLines = listOf(
                    "Termux Live Bridge not reachable at $base (${e.localizedMessage ?: "connection refused"})." to TerminalStream.STDERR
                ),
            )
        }
    }

    suspend fun executeTermuxBridge(
        bridgeUrl: String,
        token: String,
        command: String,
    ): ShellResult = withContext(Dispatchers.IO) {
        val base = bridgeUrl.trim().trimEnd('/')
        try {
            val payload = JSONObject().apply {
                put("command", command)
            }.toString()
            val (code, body) = httpPostJson("$base/exec", token, payload, timeoutMs = 120_000)
            if (code !in 200..299) {
                return@withContext ShellResult(
                    exitCode = 1,
                    newRelativeDir = ".",
                    outputLines = listOf("Termux Bridge HTTP $code: $body" to TerminalStream.STDERR),
                )
            }
            val json = JSONObject(body)
            val exitCode = json.optInt("exitCode", 0)
            val stdout = json.optString("stdout", "")
            val stderr = json.optString("stderr", "")
            val cwd = json.optString("cwd", "~/antigravity")
            val out = mutableListOf<Pair<String, TerminalStream>>()
            stdout.lines().filter { it.isNotEmpty() }.forEach { out += it to TerminalStream.STDOUT }
            stderr.lines().filter { it.isNotEmpty() }.forEach { out += it to TerminalStream.STDERR }
            ShellResult(
                exitCode = exitCode,
                newRelativeDir = ".",
                outputLines = out,
                termuxCwd = cwd,
            )
        } catch (e: Exception) {
            ShellResult(
                exitCode = 1,
                newRelativeDir = ".",
                outputLines = listOf(
                    "Termux Live Bridge unavailable at $base (${e.localizedMessage ?: "connection refused"}). Start the Termux listener first." to TerminalStream.STDERR
                ),
            )
        }
    }

    fun buildTermuxListenerCommand(port: Int = 8766, token: String): String {
        return """
mkdir -p ~/antigravity && cat << 'EOF' > ~/antigravity/.ag_bridge.py
import http.server, json, os, subprocess
TOKEN = "$token"
BASE = os.path.expanduser("~/antigravity")
STATE = {"cwd": BASE}
class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)
    def _auth(self):
        return self.headers.get("X-Antigravity-Token") == TOKEN
    def do_GET(self):
        if not self._auth(): return self._send(401, {"error": "unauthorized"})
        self._send(200, {"ok": True, "cwd": STATE["cwd"]})
    def do_POST(self):
        if not self._auth(): return self._send(401, {"error": "unauthorized"})
        n = int(self.headers.get("Content-Length", "0"))
        data = json.loads(self.rfile.read(n).decode() or "{}")
        if self.path == "/sync":
            ws = os.path.join(BASE, "".join(c if c.isalnum() or c in "_-" else "_" for c in data.get("workspace", "ws")))
            os.makedirs(ws, exist_ok=True)
            for f in data.get("files", []):
                p = os.path.normpath(os.path.join(ws, f.get("path", "file.txt").lstrip("/")))
                if p.startswith(ws):
                    os.makedirs(os.path.dirname(p), exist_ok=True)
                    open(p, "w").write(f.get("content", ""))
            STATE["cwd"] = ws
            return self._send(200, {"cwd": ws, "count": len(data.get("files", []))})
        cmd = data.get("command", "").strip()
        if cmd.startswith("cd "):
            tgt = os.path.expanduser(cmd[3:].strip())
            nxt = tgt if os.path.isabs(tgt) else os.path.normpath(os.path.join(STATE["cwd"], tgt))
            if os.path.isdir(nxt):
                STATE["cwd"] = nxt
                return self._send(200, {"exitCode": 0, "stdout": nxt, "stderr": "", "cwd": STATE["cwd"]})
            return self._send(200, {"exitCode": 1, "stdout": "", "stderr": f"cd: {tgt}: No such directory", "cwd": STATE["cwd"]})
        r = subprocess.run(cmd, shell=True, cwd=STATE["cwd"], capture_output=True, text=True, timeout=110)
        self._send(200, {"exitCode": r.returncode, "stdout": r.stdout, "stderr": r.stderr, "cwd": STATE["cwd"]})
print("Antigravity Termux Bridge listening on 127.0.0.1:$port")
http.server.ThreadingHTTPServer(("127.0.0.1", $port), H).serve_forever()
EOF
python3 ~/antigravity/.ag_bridge.py
        """.trimIndent()
    }

    private fun httpPostJson(urlString: String, token: String, payload: String, timeoutMs: Int): Pair<Int, String> {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = timeoutMs
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Antigravity-Token", token)
        }
        return try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
            code to body
        } finally {
            conn.disconnect()
        }
    }

    private fun resolveSafeSubdir(root: File, relativeDir: String): File {
        if (relativeDir.isBlank() || relativeDir == ".") return root
        val candidate = File(root, relativeDir).canonicalFile
        return if (candidate.exists() && candidate.isDirectory && candidate.path.startsWith(root.path)) {
            candidate
        } else {
            root
        }
    }

    private fun relativePathOf(root: File, target: File): String {
        val rel = target.canonicalPath.removePrefix(root.canonicalPath).trimStart(File.separatorChar)
        return rel.ifBlank { "." }
    }
}
