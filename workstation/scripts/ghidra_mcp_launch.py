"""Launch GhidraMCP 2.0 in headless mode on the binary named in research/local/ghidra-target.txt.

ghidra-mcp needs --binary at startup, but MCP client configs are static. This launcher reads
the target from a git-ignored file, so switching binaries means editing that file and
reconnecting the MCP server. It is not a config change.

Rules (CLAUDE.md): targets must come from the owner's own devices and live in research/local/.
The Ghidra project is kept in research/local/ghidra-projects/.

Environment (with defaults for this workstation):
    GHIDRA_INSTALL_DIR   %LOCALAPPDATA%/Programs/ghidra_12.1.4_PUBLIC
    GHIDRA_MCP_HOME      %LOCALAPPDATA%/Programs/ghidra-mcp-2.0
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
LOCAL = REPO / "research" / "local"
TARGET_FILE = LOCAL / "ghidra-target.txt"


def main() -> int:
    lad = Path(os.environ.get("LOCALAPPDATA", ""))
    ghidra = Path(os.environ.get("GHIDRA_INSTALL_DIR", lad / "Programs" / "ghidra_12.1.4_PUBLIC"))
    home = Path(os.environ.get("GHIDRA_MCP_HOME", lad / "Programs" / "ghidra-mcp-2.0"))
    exe = home / ".venv" / "Scripts" / "ghidra-mcp.exe"
    jar = home / "extension" / "GhidraMCP" / "lib" / "GhidraMCP.jar"

    if not TARGET_FILE.is_file():
        sys.exit(f"No target: write the path of a binary under research/local/ into {TARGET_FILE}")
    target = Path(TARGET_FILE.read_text(encoding="utf-8").strip().splitlines()[0].strip())
    if not target.is_absolute():
        target = (REPO / target).resolve()
    if LOCAL.resolve() not in target.resolve().parents:
        sys.exit(f"Refusing {target}: Ghidra targets must live under {LOCAL} (CLAUDE.md)")
    if not target.is_file():
        sys.exit(f"Target not found: {target}")

    projects = LOCAL / "ghidra-projects"
    projects.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, GHIDRA_INSTALL_DIR=str(ghidra))
    cmd = [str(exe), "--headless", "--binary", str(target), "--project", str(projects),
           "--extension-jar", str(jar)]
    # stdio is inherited, so the MCP client talks straight to ghidra-mcp.
    return subprocess.call(cmd, env=env)


if __name__ == "__main__":
    sys.exit(main())
