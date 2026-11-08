# AGENTS.md

Instructions for any coding agent in this repository: OpenCode (local Qwen or Claude), Claude Code, or others. OpenCode V2 reads only this file, so it points to the authoritative documents.

## Read first

1. [`CLAUDE.md`](CLAUDE.md): **the standing rules. It is authoritative and applies to every model, not just Claude.**
2. [`README.md`](README.md): scope and routes A, B and C.
3. [`PICKUP.md`](PICKUP.md): the ordered on-device playbook.
4. [`research/README.md`](research/README.md): the research database layout.

If this file and `CLAUDE.md` disagree, `CLAUDE.md` wins.

## Non-negotiables (summary of CLAUDE.md)

- **No cracking and no patching.**
  - Never bypass signature, permission, UID or app-authentication checks.
  - Never extract vendor keys or tokens.
  - Never modify OnePlus apps or firmware.
  - A route that needs any of these is recorded as a finding, not pursued as a task.
- **The tablet is read-only.** Ask the owner before anything that changes device state: installs, settings, Shizuku, HCI snoop, pen writes, Frida or any other runtime instrumentation.
- **Static analysis is allowed** (JADX, Ghidra) on OnePlus APKs, native libraries, kernel modules and firmware, limited to the pen-haptics path. Allowed sources:
  - files pulled from the owner's own devices;
  - official OnePlus OTA packages from OnePlus's own servers;
  - HiPaint from APKMirror, the only mirror allowed. Everything pulled or decompiled stays in `research/local/`, which git ignores. Commit findings only, never code or binaries.
- **"Works" means the pen physically vibrated.** Never report a refused or failed call as success.

## Evidence

- **Source of truth:** `research/data/*.json`. The status values are `verified`, `unverified`, `inferred` and `open`, as defined in `research/schema/record.schema.json`.
  - `verified` needs a device read or observation, with the build and date in `basis`.
  - Never upgrade a status without new primary evidence.
- **Before an analysis:** search the existing records and `stylo2/docs/` first, and don't repeat work.
- **Before committing:** run `py -3 research/tools/validate.py`.
- **Any SQLite/FTS/embedding index** is a derived, git-ignored cache rebuilt from the JSON. Never edit it as a ledger.

## Working style

- **Keep context small.** Use search and focused reads, and write short reports. Never feed a whole decompiled APK into a prompt.
- **Git:** work on a feature branch and open draft PRs. No force-pushes.
- **Model roles:**
  - Local Qwen handles routine reading, documentation and tool use.
  - Claude handles conflicting evidence, Binder and security reasoning, architecture and review.
  - Hand work off through files in the repo.
