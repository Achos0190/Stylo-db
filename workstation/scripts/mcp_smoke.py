"""Smoke-test an MCP server over stdio, independent of any LLM or agent.

Starts the server, lists its tools and optionally calls one tool with JSON arguments.
Uses the `mcp` reference SDK (installed with `fastmcp`).

Usage:
    python mcp_smoke.py [--call TOOL [--args JSON]] [--env K=V ...] -- <server command...>
Example:
    python mcp_smoke.py --call get_android_manifest -- path/to/python.exe path/to/jadx_mcp_server.py
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client


async def run(cmd: list[str], env: dict[str, str] | None, call: str | None, call_args: dict, max_chars: int) -> int:
    params = StdioServerParameters(command=cmd[0], args=cmd[1:], env=env)
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            init = await session.initialize()
            info = _attr(init, "server_info", "serverInfo")
            print(f"server: {info.name} {info.version} (protocol {_attr(init, 'protocol_version', 'protocolVersion')})")
            tools = (await session.list_tools()).tools
            print(f"{len(tools)} tools: " + ", ".join(sorted(t.name for t in tools)))
            if call:
                result = await session.call_tool(call, call_args)
                text = "\n".join(getattr(c, "text", repr(c)) for c in result.content)
                is_error = bool(_attr(result, "is_error", "isError"))
                print(f"--- {call} -> {'ERROR' if is_error else 'OK'} ({len(text)} chars) ---")
                print(text[:max_chars] + ("\n[...truncated]" if len(text) > max_chars else ""))
                return 1 if is_error else 0
    return 0


def _attr(obj, *names):
    """The MCP SDK renamed camelCase fields to snake_case between majors; accept both."""
    for n in names:
        if hasattr(obj, n):
            return getattr(obj, n)
    raise AttributeError(names[0])


def main() -> None:
    argv = sys.argv[1:]
    if "--" not in argv:
        sys.exit(__doc__)
    split = argv.index("--")
    ap = argparse.ArgumentParser()
    ap.add_argument("--call")
    ap.add_argument("--args", default="{}")
    ap.add_argument("--env", action="append", default=[])
    ap.add_argument("--max-chars", type=int, default=1500)
    opts = ap.parse_args(argv[:split])
    env = None
    if opts.env:
        import os

        env = dict(os.environ)
        env.update(kv.split("=", 1) for kv in opts.env)
    sys.exit(asyncio.run(run(argv[split + 1 :], env, opts.call, json.loads(opts.args), opts.max_chars)))


if __name__ == "__main__":
    main()
