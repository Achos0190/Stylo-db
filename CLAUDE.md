# Working in this repository

Open Stylo 2 haptics research. The scope is in `README.md`, and the on-device playbook is in `PICKUP.md`. Read both before working with the tablet. The playbook's steps are also available as the skills `pad3-readout`, `route-a-poc` and `record-findings` in `.claude/skills/`.

## Rules that always apply

- **No cracking.** Don't bypass signature, permission or app-authentication checks, or extract or reuse vendor signing keys, certificates or tokens. If a route needs any of that, record it as a finding and stop that route.
- **No patching** of OnePlus apps or firmware, ever.
- **Decompiling: owner-approved scope (widened 7 Nov 2026, replacing the 2 Nov 2026 exception).** The owner allows static analysis of OnePlus software pulled from their own devices (the Pad 3 and the OnePlus 12), to understand the pen-haptics interface for interoperability. The conditions:
  - **What may be analysed:**
    - JADX on `com.oplus.ipemanager`, OnePlus Notes and HiPaint, the whole APK and not only the AIDL classes.
    - Ghidra on the native libraries those apps ship, on the pen-related kernel modules (such as `oplus_wireless_pen.ko`) and on the CPS8601 firmware blob.
  - **Sources:** copies pulled from the owner's own devices with `adb pull`. The owner added two more sources on 7 Nov 2026:
    - **Official OnePlus OTA packages** for the owner's device models (such as OPD2415), downloaded from OnePlus/Oplus's own OTA servers (`*.allawnofs.com`, `*.oneplus.com`). Record the URL and SHA-256 next to the file. Third-party re-hosts of firmware images are not allowed.
    - **HiPaint (Aige Studio) from APKMirror.** This is the only mirror download allowed. Record the version, SHA-256 and signer certificate digest, which may be read for this check. No other mirror downloads.
    - Evidence from a package whose build differs from the tablet's must name that build in `basis`. It does not verify behaviour on the tablet's own build.
  - **Purpose:** the pen-haptics path, meaning binding, permission and caller checks, the haptic call chain, the transport to the pen and the firmware layout. Don't go beyond what the haptics question needs.
  - **Where files stay:** APKs, binaries, decompiled output and Ghidra projects stay in `research/local/`, which git ignores.
  - **What gets committed:** only findings, such as names, descriptors, signatures, transaction codes, constants, check locations and short excerpts of a few lines as evidence. Never commit decompiled code wholesale or redistribute OnePlus binaries.
  - **Check logic** (permission, UID, package or signature checks) may be read and documented. It must never be circumvented (see "No cracking").
  - The legal basis is EU Software Directive Art. 6 (see `stylo2/docs/LEGAL.md`). The AIDL check is still PICKUP.md Step 2b, through `research/tools/aidl_check.py`.
  - **Runtime instrumentation (Frida or similar)** is not covered by this exception. It changes process state on the device, so it needs the owner's go-ahead each time, like any other tablet change.
  - Reading an APK's public signer fingerprint (`apksigner verify --print-certs`) still needs the owner's go-ahead.
- **The tablet is read-only.** Only run reads (`getprop`, `ls`, `cat`, `dumpsys`, `pm path`/`pm list`, `logcat -d`, `dmesg`, `modinfo`, `sha256sum`, `find`, `adb pull`, including pulling an installed APK). Use `research/tools/pad3_readout.py` where it covers the step. It enforces this.
- **Ask the owner first** before anything that changes the tablet: installing or uninstalling an app (including this repo's own apps), changing a setting (including the Bluetooth HCI snoop log), starting Shizuku, or writing to the pen.
- **Root:** never needed for what this project ships. If the tablet is rooted, `su` may be used for reads only.
- **Pen writes:** only bytes captured from OnePlus Notes on this device. No fuzzing.
- **Never report an unauthorised or failed call as success.** "Works" means the pen physically vibrated.
- **Pen input and OnePlus Notes must keep working.** If probing disrupts them, stop and tell the owner.

## Data

- Pulled files (firmware, kernel modules, APKs, full getprop dumps, logs, snoop captures) go in `research/local/`, which git ignores. Never commit them.
- Commit only hashes, sizes, short excerpts and findings. Redact Bluetooth addresses, serial numbers, IMEIs and account names as `<redacted>`.
- Results go in `research/data/*.json`. Set `status` honestly (`verified` only for what was read or observed on the device, with the build and date in `basis`), and set `updated` on every record you change. Route results also go in the `stylo2/docs/E-prototype.md` matrix.
- Run `python3 research/tools/validate.py` before committing. Use `py -3` instead of `python3` on Windows.

## Code

- Android modules build from the root: `./gradlew :stylo2-poc:assembleDebug`, `:stylus-ble-inspector:assembleDebug`, `:stylo2-bridge:assembleDebug`, `:app:assembleDebug`.
- Rust: `cd stylo2 && cargo test --workspace`. The public API shouldn't change. Findings fill the backend seams (`Stylo2GattProfile`, the transports, the `wire` mapping).
- `app/` is upstream code (the privileged route). Leave it unchanged unless the owner asks.
- Don't change AIDL transaction codes, interface descriptors or profile integers without device evidence.

## Git

Work on a feature branch and keep PRs as drafts until the owner reviews them. Don't force-push shared history.
