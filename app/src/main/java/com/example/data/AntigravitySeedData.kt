package com.example.data

import com.example.model.AgentMode
import com.example.model.ArtifactKind
import com.example.model.BuiltinTool
import com.example.model.GeminiModelOption

data class SeedFileSpec(
    val id: String,
    val path: String,
    val language: String,
    val content: String,
    val previousContent: String = "",
)

object AntigravitySeedData {
    const val DEFAULT_WORKSPACE_ID = "ws_antigravity_sdk"
    const val DEFAULT_SESSION_ID = "sess_sdk_bootstrap"
    const val DEFAULT_PLAN_ARTIFACT_ID = "art_sdk_plan"
    const val DEFAULT_HTML_ARTIFACT_ID = "art_sdk_preview"

    val starterFiles = listOf(
        SeedFileSpec(
            id = "file_agent_quickstart",
            path = "agent_quickstart.py",
            language = "python",
            content = """
import asyncio
from google.antigravity import Agent, LocalAgentConfig, CapabilitiesConfig, hooks, types
from google.antigravity.types import BuiltinTools


@hooks.pre_turn
async def guard_destructive_commands(prompt: str) -> types.HookResult:
    if "rm -rf" in prompt or "drop table" in prompt.lower():
        return types.HookResult(
            allow=False,
            reason="Blocked by @hooks.pre_turn safety policy."
        )
    return types.HookResult(allow=True)


async def main():
    config = LocalAgentConfig(
        capabilities=CapabilitiesConfig(
            enabled_tools=[
                BuiltinTools.READ_FILE,
                BuiltinTools.WRITE_FILE,
                BuiltinTools.RUN_COMMAND,
                BuiltinTools.SEARCH_CODE,
            ]
        ),
        hooks=[guard_destructive_commands],
    )

    async with Agent(config) as agent:
        response = await agent.chat(
            "Inspect the workspace and summarize recent SDK updates."
        )
        print(await response.text())


if __name__ == "__main__":
    asyncio.run(main())
""".trimIndent(),
            previousContent = """
import asyncio
from google.antigravity import Agent, LocalAgentConfig

async def main():
    config = LocalAgentConfig()
    async with Agent(config) as agent:
        response = await agent.chat("What files are in the current directory?")
        print(await response.text())

if __name__ == "__main__":
    asyncio.run(main())
""".trimIndent(),
        ),
        SeedFileSpec(
            id = "file_local_config",
            path = "local_config.py",
            language = "python",
            content = """
from google.antigravity import LocalAgentConfig, CapabilitiesConfig
from google.antigravity.types import BuiltinTools
from google.antigravity.triggers import every


async def background_health_check(ctx):
    print("[Antigravity Trigger] Checking workspace diagnostics...")


def build_readonly_config() -> LocalAgentConfig:
    return LocalAgentConfig(
        capabilities=CapabilitiesConfig(
            enabled_tools=BuiltinTools.read_only()
        ),
        triggers=[every(60, background_health_check)],
    )
""".trimIndent(),
        ),
        SeedFileSpec(
            id = "file_design_md",
            path = "DESIGN.md",
            language = "markdown",
            content = """
# Google Antigravity Design Tokens

- Primary / Brand Surface: `#121317`
- Elevated Surface: `#191B22`
- Light Surface: `#FFFFFF`
- Secondary Text Charcoal: `#45474D`
- Primary Font: `Google Sans Flex`
- Monospace Editor Font: `JetBrains Mono`
- Ecosystem Accents: `#4285F4`, `#34A853`, `#FBBC04`, `#EA4335`
""".trimIndent(),
        ),
        SeedFileSpec(
            id = "file_preview_html",
            path = "preview.html",
            language = "html",
            content = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <style>
    body {
      margin: 0;
      padding: 20px;
      background: #121317;
      color: #e8eaed;
      font-family: system-ui, -apple-system, sans-serif;
    }
    .card {
      background: #191b22;
      border: 1px solid #2f3441;
      border-radius: 14px;
      padding: 18px;
      margin-bottom: 14px;
    }
    .pill {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      background: rgba(138, 180, 248, 0.16);
      color: #8ab4f8;
    }
    .dot-row { display: flex; gap: 8px; margin-top: 12px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; }
  </style>
</head>
<body>
  <div class="card">
    <span class="pill">google-antigravity SDK v1.0</span>
    <h2 style="margin: 10px 0 6px;">Agent Runtime Connected</h2>
    <p style="color: #9aa0a6; font-size: 14px; margin: 0;">
      Browser Subagent (/browser) live artifact preview rendered inside Antigravity.
    </p>
    <div class="dot-row">
      <span class="dot" style="background:#4285F4"></span>
      <span class="dot" style="background:#EA4335"></span>
      <span class="dot" style="background:#FBBC04"></span>
      <span class="dot" style="background:#34A853"></span>
    </div>
  </div>
</body>
</html>
""".trimIndent(),
        ),
    )

    val starterPlanMarkdown = """
# Implementation Plan: Antigravity SDK Safety Hooks

## Objective
Enforce `@hooks.pre_turn` command validation and configure `BuiltinTools` permissions before executing autonomous agent turns.

## Changes Applied
- [x] Added `guard_destructive_commands` pre-turn hook in `agent_quickstart.py`
- [x] Configured `CapabilitiesConfig(enabled_tools=...)` for least-privilege execution
- [ ] Run `python agent_quickstart.py` verification in integrated terminal
- [ ] Review generated diff and approve walkthrough

## Verification
Inspect the diff in **Artifacts** or run `antigravity status` inside the **Editor** terminal drawer.
""".trimIndent()

    fun buildUnifiedDiff(oldText: String, newText: String, path: String): String {
        if (oldText.isBlank()) {
            val lines = newText.lines().joinToString("\n") { "+ $it" }
            return "--- /dev/null\n+++ b/$path\n@@ -0,0 +1,${newText.lines().size} @@\n$lines"
        }
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val oldSet = oldLines.toSet()
        val newSet = newLines.toSet()
        val sb = StringBuilder()
        sb.appendLine("--- a/$path")
        sb.appendLine("+++ b/$path")
        sb.appendLine("@@ -1,${oldLines.size} +1,${newLines.size} @@")
        oldLines.forEach { line ->
            if (line !in newSet) sb.appendLine("- $line")
            else sb.appendLine("  $line")
        }
        newLines.forEach { line ->
            if (line !in oldSet) sb.appendLine("+ $line")
        }
        return sb.toString().trimEnd()
    }

    fun generatePythonSdkSnippet(
        modelId: String,
        mode: AgentMode,
        readOnly: Boolean,
        enabledTools: List<String>,
        blockDestructiveHooks: Boolean,
    ): String {
        val activeTools = if (readOnly) {
            "BuiltinTools.read_only()"
        } else {
            val list = enabledTools.mapNotNull { name ->
                BuiltinTool.entries.firstOrNull { it.name == name }?.sdkName
            }
            if (list.isEmpty()) "[]" else "[\n            " + list.joinToString(",\n            ") + "\n        ]"
        }
        val hookDef = if (blockDestructiveHooks) {
            """
@hooks.pre_turn
async def audit_prompt(prompt: str) -> types.HookResult:
    if "rm -rf" in prompt:
        return types.HookResult(allow=False, reason="Destructive commands are restricted.")
    return types.HookResult(allow=True)

""".trimStart()
        } else ""

        val hookParam = if (blockDestructiveHooks) ",\n    hooks=[audit_prompt]" else ""

        return """
# pip install google-antigravity
import asyncio
from google.antigravity import Agent, LocalAgentConfig, CapabilitiesConfig, hooks, types
from google.antigravity.types import BuiltinTools

${hookDef}config = LocalAgentConfig(
    # Runtime Mode: ${mode.label} | Model: $modelId
    capabilities=CapabilitiesConfig(
        enabled_tools=$activeTools
    )$hookParam
)

async def main():
    async with Agent(config) as agent:
        response = await agent.chat("Analyze workspace and prepare implementation plan.")
        print(await response.text())

if __name__ == "__main__":
    asyncio.run(main())
""".trimIndent()
    }
}
