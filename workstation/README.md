# Workstation: local agents and MCP toolchain

This sets up the tooling that lets coding agents work on this repository. The agents are OpenCode with a local Qwen model, OpenCode with Claude, and Claude Code. The research itself lives elsewhere: the rules are in [`../CLAUDE.md`](../CLAUDE.md) and the playbook is [`../PICKUP.md`](../PICKUP.md).

Everything installs **per user, with no admin rights**. Every download is checked against its published SHA256. Machine-specific config stays out of git.

## Installed components (2026-11-07)

| Component | Version | Location |
|---|---|---|
| OpenCode (V2) | 2.2.6 | `%LOCALAPPDATA%\Programs\OpenCode` (on the user PATH) |
| LM Studio runtime | llama.cpp ROCm 2.71.0 | existing install, unchanged |
| Local model | Qwen3.8-27B, unsloth UD-Q3_K_XL | existing LM Studio model |
| Claude in OpenCode | "Claude Agent" provider | reuses the Claude Code login |
| JADX | 1.5.6 | `%LOCALAPPDATA%\Programs\jadx-1.5.6` |
| jadx-ai-mcp plugin and server | 6.5.0 | JADX plugin store, plus `…\jadx-mcp-server-6.5.0` |
| Android MCP | @us-all/android-mcp 1.14.4 | `…\android-mcp` |
| Ghidra | 12.1.4 (not 12.2, see below) | `…\ghidra_12.1.4_PUBLIC` |
| GhidraMCP (LaurieWired) | 2.0, headless | `…\ghidra-mcp-2.0` |
| Frida | not installed | see [Frida](#frida-assessed-not-installed) |

**Install or reinstall:**
- `install/Install-OpenCode.ps1`
- `install/Install-McpToolchain.ps1`

Both scripts are idempotent.

## Local model

**Why Qwen3.8-27B.** It is the strongest coding model that fits 16 GB of VRAM plus 32 GB of RAM:
- SWE-bench Pro 61.7 and Terminal-Bench 2.1 73.0, from Qwen's model card.
- It outperforms Qwen3-Coder-30B-A3B (about 50 on SWE-bench Verified) and Qwen3.6-35B-A3B.
- The bigger Qwen3-Coder-Next and Qwen3.8-Flash-Next don't fit.

**Start it before OpenCode:**

```powershell
lms server start
workstation\.venv\Scripts\python.exe workstation\scripts\load_local_model.py   # 64K ctx, flash attention, q8_0 KV
```

The script uses the LM Studio Python SDK because `lms load` cannot set flash attention or the KV-cache type. To set up the venv: `py -3 -m venv workstation\.venv; workstation\.venv\Scripts\pip install lmstudio`.

**Measured on an RX 7800 XT** (Windows uses about 2 GB of the VRAM itself):

| Configuration | 14K-token prompt (prefill / generation) | 41K-token prompt |
|---|---|---|
| Defaults (no flash attention, f16 KV), 32K ctx | 190 / 8.9 tok/s | over the context limit |
| Defaults, 48K or 64K ctx | ROCm OOM crash | n/a |
| **Flash attention + q8_0 KV, batch 512, 64K ctx** | **572 / 26 tok/s** | **444 / 17 tok/s** |

**Other measurements:**
- Peak VRAM is 14.3 GB.
- At 96K context it runs out of memory.
- MTP speculative decoding added about 20% on short prompts, but only `lms load` can turn it on, and that command can't set flash attention.

**Pitfalls:**
- OpenCode's system prompt plus tool definitions is about **9.2K tokens**. If OpenCode asks for a model that isn't loaded, LM Studio JIT-loads it with its default context, and the request fails. Always pre-load the model.
- Benchmark with prompts of 10K tokens or more. Short prompts hide the memory problems.

**Claude:** run `opencode auth login claude-agent` once. Then use `-m claude-agent/sonnet` or `claude-agent/opus`, or pick a model in `/models`. It reuses the Claude Code subscription login, so no API key is needed.

## MCP servers

All three servers are wired into **both** OpenCode and Claude Code:
- OpenCode: `.opencode/opencode.jsonc`, which git ignores. The template is `config/opencode.project.example.jsonc`.
- Claude Code: added with `claude mcp add --scope local …`.

| Server | Tools | How to use it | Safety |
|---|---|---|---|
| `jadx` | 34 (source, smali, manifest, xrefs, search, resources; rename and comment act on the JADX project only) | Open the APK in JADX-GUI first: `jadx-gui.bat research\local\…\base.apk`. The plugin serves `127.0.0.1:8650`. | The plugin binds **all interfaces** (`::`) with no auth. The Windows Firewall blocks inbound traffic to the JDK's `javaw.exe`, so it isn't reachable from the network. **Never click "Allow" on a Java firewall prompt.** |
| `android` | 76 (devices, packages, dumpsys-style info, logcat, files, screenshots, …) | Uses the existing `adb.exe` through `ADB_PATH`. | **Read-only.** `ANDROID_MCP_ALLOW_WRITE` and `ANDROID_MCP_ALLOW_SHELL` are left unset, so install, tap, push, settings and `execute-shell` are refused (verified). Turning them on needs the owner's approval (CLAUDE.md). |
| `ghidra` | 28 (decompile, disassemble, xrefs, strings, imports and exports, memory, rename, comment) | Headless, so no GUI is needed. Put the target's path on the first line of `research/local/ghidra-target.txt`, then reconnect the MCP. Projects persist in `research/local/ghidra-projects/`. | The launcher refuses targets outside `research/local/`. |

**Validate any server without an LLM:**

```powershell
# replace <server venv python> with the server's own venv python.exe, e.g. %LOCALAPPDATA%\Programs\jadx-mcp-server-6.5.0\.venv\Scripts\python.exe
python workstation\scripts\mcp_smoke.py [--call TOOL --args '{...}'] -- <server command>
```

**Validation results (2026-11-07):**
- `jadx`: read `app/release/app-release.apk`'s manifest through the plugin.
- `android`: listed 0 devices (no tablet attached), and `execute-shell` was refused.
- `ghidra`: imported, analysed and decompiled a test PE (`AdbWinUsbApi.dll`).
- **End to end:** local Qwen3.8-27B in OpenCode called `android` and `ghidra` in one code-mode step and reported correctly. In Claude Code, all three servers show ✔ Connected.

### Why these servers, and not the alternatives

The candidates were vetted on 2026-11-07.

- **JADX: zinja-coder/jadx-ai-mcp.** About 3.0k stars, Apache-2.0, release 6.5.0 on 2026-10-19. The Python server's code was reviewed: it only talks to `127.0.0.1:8650`, with `trust_env=False`, and spawns no subprocesses.
- **Android: us-all/android-mcp-server.** MIT and small (16 stars), but read-only by default with explicit write and shell gates. It is a pinned install, not `npx @latest`. minhalvp/android-mcp-server was rejected: stale since May 2025, with an unrestricted `execute_adb_command`.
- **Ghidra: LaurieWired/GhidraMCP 2.0** rather than bethington/ghidra-mcp v7.0.0:
  - LaurieWired has about 28 tools against about 219. That matters for a local model's context budget.
  - Its backend is loopback-only, and its headless mode is pip-installable.
  - bethington is still a good choice when more depth is needed: use v7.0.0 with Ghidra 12.1.4 and Java 21 (its `main` already requires Ghidra 12.2 and Java 25), with the read-only allowlist and scripts disabled.
- **Ghidra 12.1.4, not 12.2.** Both MCP extensions only load in the exact Ghidra version they were built against.

## Frida (assessed, not installed)

- **No Frida MCP meets the bar.**
  - yfe404/frida-mcp has **no licence**, and the npm package its README tells you to install (`frida-mcp`) is published by a different, unlinked account.
  - dnakov/frida-mcp has been stale since May 2025 and probably breaks on Frida 17.
- **Frida doesn't fit this project's rules either:**
  - Instrumenting OnePlus processes needs `frida-server` on the tablet, which means root, a device change.
  - Embedding Frida Gadget would mean repackaging OnePlus apps, which is **patching** and is forbidden.
  - What remains legitimate is instrumenting **this repo's own** PoC app. That adds little over logcat and the app's own step-by-step result reporting.
- **Decision:** don't install Frida. Revisit only if the `check` step of `pad3_readout.py` shows root and the owner approves runtime instrumentation for a specific question (CLAUDE.md treats it as a device change).

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `exceeds the available context size (8192 tokens)` | The model was JIT-loaded with defaults. Run `load_local_model.py`, then retry. Check with `lms ps`. |
| `terminated` or `ROCm error: out of memory` in `~\.lmstudio\server-logs` | Context too large, or flash attention or q8 KV is off. Reload with the script. Unload stray models (`lms ps`, `lms unload <id>`). |
| Slow generation (under 10 tok/s) on long prompts | Same cause: VRAM saturation. Close GPU-heavy apps. Check there is about 1.5 GB of VRAM free after loading. |
| `opencode` not found | Open a new terminal; the PATH change applies to new sessions only. |
| `opencode mcp list` says "No MCP servers configured" | It doesn't read the project config. The servers still load in `opencode run` and in the TUI started in the repo. |
| jadx tools fail with a connection error | JADX-GUI isn't running, or no APK is open. |
| ghidra server exits immediately | `research/local/ghidra-target.txt` is missing, or it points outside `research/local/`. |
| `lms get` stalls | Known problem on this connection. Download with `curl.exe -C - --retry …` into `~\.lmstudio\models\<publisher>\<repo>\` instead. |
