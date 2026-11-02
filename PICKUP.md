# PICKUP: local session with the OnePlus Pad 3

The playbook for a Claude Code session on the computer the Pad 3 and Stylo 2 are connected to. It replaces the two earlier handoffs (the firmware read-out prompt and the route-testing brief) with one ordered plan. The project's scope is in [`README.md`](README.md), and the standing rules are in [`CLAUDE.md`](CLAUDE.md).

## For the person: before starting

1. Clone the repository, check out the working branch, and pull.
2. Install what the session needs:
   - **Android platform-tools** (`adb`) on `PATH`.
   - **Python 3**.
   - **Android Studio** (or JDK 17+ and the Android SDK, API 36).
   - Optionally **Rust**.
3. On the tablet, turn on Developer options and USB debugging, then check that `adb devices` lists it as `device`. Wireless debugging (`adb pair`, `adb connect`) frees the tablet for drawing during the tests.
4. Start Claude Code in the repository root and say:

   > Read `PICKUP.md` and follow it. Ask me before anything that changes the tablet.

---

## For Claude

### What is already known

The status labels below match the database (`research/data/`).

- Haptics: apps → `com.oplus.ipemanager` (`.btadsorb.CoreService`, action `com.oplus.ipemanager.ACTION.PENCIL_SDK`, permission `com.oplus.permission.safe.IOT`) → Bluetooth LE → pen. OnePlus Notes holds the permission. *Verified from the upstream app code and community sources. Not yet read from this tablet.*
- Before any of that, the pen's magnetic charger chip pairs it. The chip is the **CPS8601**, driven by `oplus_wireless_pen.ko`. On attach, it receives the pen's BLE address over the charging link (`Rx attach!` → `tx wakeup!` → `receive ask pkt!` → `get ble addr` → `report 1`). Write-up: [`research/docs/pen-attach-handshake.md`](research/docs/pen-attach-handshake.md).
- The chip loads `/odm/firmware/wireless_pen/<ro.boot.prjname>/cps8601_firmware.bin`. After a Chinese-to-global ROM conversion, that folder can be missing, and the pen never pairs. Community modules fix this by copying firmware into the right folder.
  - Known project numbers: `23926`, `23927`, `23976` and `23978` (Pad Pro / Pad 2), plus `24926` and `24976` (Pad 2 Pro, probably the same family as the Pad 3).
  - Known firmware builds: `cfac6737…` (the `239xx` folders) and `33a4ab21…` (`24926`). No `24976` copy has been examined yet.
- **Nothing has been tested on a device.** The Rust tests, Android compilation and database validation are all host-only.

**Not established: whether this tablet is rooted, or has a pen-fix module installed.** The two earlier sessions assumed differently. The `check` step settles it. Don't assume either way.

### Ground rules

Everything in `CLAUDE.md` applies. In short:

- The tablet is read-only.
- Root, if present, is used for reads only.
- Ask before anything that changes the tablet. That includes installing this repo's own apps, turning on the HCI snoop log, and starting Shizuku.
- Write to the pen only bytes captured from OnePlus Notes.
- A route that needs a bypass is a finding, not a task.
- "Works" means the pen physically vibrated.

### Step 0: host baseline (about 5 minutes)

```bash
git status && git branch --show-current
python3 research/tools/validate.py                       # OK
(cd stylo2 && cargo test --workspace)                    # 26 passed (skip if Rust isn't installed)
./gradlew :stylo2-poc:assembleDebug :stylus-ble-inspector:assembleDebug
```

On Windows, use `py -3` for `python3` and `.\gradlew` for `./gradlew`.

### Step 1: tablet check (read-only)

```bash
python3 research/tools/pad3_readout.py check
```

This reports the model, the project number, the build, the boot state, whether `su` works, and which of the pen service, the PoC, the inspector, the upstream companion app and Shizuku are installed. If a root prompt appears on the tablet, the person approves or denies it. Either answer is fine.

### Step 2: read-out and firmware retrieval (read-only, about 5 minutes)

```bash
python3 research/tools/pad3_readout.py all
```

This runs `identify`, `firmware`, `modules`, `driver`, `service` and `bluetooth`. Everything lands in `research/local/pad3/`, which git ignores:

| Step | What it settles |
| --- | --- |
| `identify` | `ro.boot.prjname` (24926 or 24976?), model, build, region properties that might tell Chinese from global hardware |
| `firmware` | Folders under `/odm/firmware/wireless_pen/`, symlinks, whether the `prjname` folder exists. It hashes each file on the tablet, **pulls the firmware**, checks the hashes match, and runs `fwinfo.py` on each file against the known builds. Any `cps8601` file elsewhere on `/odm` or `/vendor` is listed too |
| `modules` | (root only) installed modules and versions, which ones ship firmware, overlay mounts (jjhitel's `/dev/anyfs/upper/...`) |
| `driver` | `oplus_wireless_pen.ko`: loaded?, path, `modinfo`, `vermagic`. It also pulls the module and pulls out firmware-path strings (does it contain `wireless_pen/%s/cps8601_firmware.bin`, and what fills `%s`?) |
| `service` | `com.oplus.ipemanager` version, and whether `CoreService`, `PENCIL_SDK` and `safe.IOT` appear. Also **the protection level of `safe.IOT`**, read two ways, the permission allowlist files, and which pen-client packages (Notes, ...) hold the permission |
| `bluetooth` | Pen bond present (addresses redacted), HCI snoop mode |

Then:

```bash
python3 research/tools/fwinfo.py --diff <a.bin> <b.bin>   # for each pair of distinct builds
python3 research/tools/fwinfo.py --strings <file>          # for any unknown build
```

The `--strings` output goes to the terminal, so keep it out of git. Read `research/local/pad3/summary.md`, which is redacted and safe to quote.

**Reading the `safe.IOT` protection level.** If it includes `signature`, an ordinary app can never be granted it. Route A should then fail at bind, and the only open options are Route B and official authorisation. Note this before Step 3.

### Step 3: Route A, the PoC app (ask first: it installs an app)

```bash
./gradlew :stylo2-poc:installDebug
```

On the tablet:

1. Draw in Notes once to confirm the pen works.
2. Open **Stylo2 Haptics PoC**.
3. Tap **1. Bind / probe service**.
4. Pick a profile and tap **2. Trigger haptic**. **Watch the pen.**
5. Tap **3. Stop feedback**.

Then:

```bash
python3 research/tools/pad3_readout.py poc-log    # captures the log and asks whether the pen vibrated
```

What each outcome means, following [`android/stylo2-poc/README.md`](android/stylo2-poc/README.md) and [`stylo2/docs/H-trust-boundaries.md`](stylo2/docs/H-trust-boundaries.md):

| Log shows | Boundary | Next |
| --- | --- | --- |
| Bind and authorisation pass, and **the pen vibrates** | none: Route A works unprivileged | Map the profiles (`setVibrationType` 0–4 and how each feels), then the Kotlin `BinderTransport` |
| `SecurityException` or `bindService returned false` at BIND | B1: permission enforced | Route B |
| Bind passes but the AUTHZ query throws | B2: the service checks the caller | Route B, or official SDK |
| Calls return but there is no vibration | B4: the session or protocol gates the motor | Route B snoop |
| Service not installed or not found | missing on this build | Re-check Step 2 |

Never record "works" unless the pen physically vibrated.

### Step 4: attach handshake (physical, guided)

```bash
python3 research/tools/pad3_readout.py handshake
```

The tool asks the person to detach, attach, detach and re-attach the pen. It then reads `dmesg` (this needs root on most builds; without root the tool says so and uses logcat only) and `logcat`. It compares the sequence with the known markers and keeps the firmware-load lines and the `ipemanager` lines (addresses redacted).

### Step 5: Route B, direct Bluetooth LE (ask first: changes a setting)

Turning on the HCI snoop log is a settings change, and Shizuku and BlueShark are extra apps. Get the person's go-ahead, then follow [`stylo2/docs/G-local-continuation.md`](stylo2/docs/G-local-continuation.md):

- **§4**: one capture of Notes drawing and starting and stopping feedback. This alone shows whether haptic control is ATT/GATT at all.
- **§5**: split-screen tandem method.
- **§6**: inspector, independent access and the non-interference test.
- [`stylo2/docs/J-capture-plan.md`](stylo2/docs/J-capture-plan.md): the nine captures and the recording schema.

Save snoop files in `research/local/pad3/snoop/`. Only write to the pen the values captured from Notes.

### Step 6: record and commit

Record the results in the database (`research/data/`). Set `updated` on every record you change, mark what you read from the tablet as `verified` with a basis like "Read from the Pad 3 (OPD2415, build …) on <date>", and mark conclusions `inferred`.

- `devices.json` → `dev-oneplus-pad3`: project number, model, build, firmware folders and builds, root and fix-module state.
- `interfaces.json` → `if-cps8601-firmware` (builds, hashes, folders), `if-oplus-wireless-pen-driver` (module info, path format), `if-oplus-ipemanager` (version, confirmed names, `safe.IOT` protection level).
- `gaps.json`: answer or narrow each open question with direct evidence only. Start with `gap-pen-firmware-directory` and the route gaps listed below.
- `sources.json`: add `src-pad3-readout`, describing what was read and when, with no identifiers.
- `research/reference/pad3-firmware/MANIFEST.md`: paths, sizes and hashes, in the style of the cxzlw manifest. No binaries.
- Fill in the Pad 3 row, the firmware table and the handshake section of `research/docs/pen-attach-handshake.md`.
- Route results go in the `stylo2/docs/E-prototype.md` matrix (with method tags: inspection / static / adb / physical). Promote confirmed protocol facts in `stylo2/docs/C-protocol.md`, and code findings in `stylo2/crates/` (`Stylo2GattProfile`, `wire::reported_code`).

```bash
python3 research/tools/validate.py && python3 research/tools/build.py
git status        # nothing under research/local/ or research/build/ may be staged
git add research stylo2 && git commit -m "Record Pad 3 read-out: firmware, driver, service, handshake"
```

If the session has the Artifact tools, sync the changed records to the database page as described in `research/README.md`. Otherwise list the changed record IDs in the report.

### Open questions, in the order a device session answers them

<!-- gap-list -->

### Report back

Give the person a short summary covering:

- the project number and build, root status, and any fix module;
- the firmware folders and which build each holds;
- the driver version and path format;
- the `safe.IOT` protection level;
- the Route A outcome, and whether the pen vibrated;
- the handshake result;
- Route B progress;
- what was committed and what is still open.

### Stop and ask the owner when

- something would change the tablet;
- a route needs access the session doesn't have, such as a permission or vendor authorisation;
- probing disrupts pen input, the Bluetooth link or Notes;
- progress would need anything on the "no cracking" list.

## Appendix: manual commands

Use these if the tool can't run. All are read-only. In PowerShell, use double quotes outside and single quotes inside, and `Select-String` instead of `grep`.

```powershell
adb shell getprop ro.boot.prjname
adb shell ls -lR /odm/firmware/wireless_pen/
adb shell "su -c 'sha256sum /odm/firmware/wireless_pen/*/*.bin'"
adb pull /odm/firmware/wireless_pen research/local/pad3/wireless_pen
adb shell "su -c 'cat /data/adb/modules/*/module.prop'"
adb shell "su -c 'find /vendor /vendor_dlkm /odm -name oplus_wireless_pen.ko 2>/dev/null'"
adb shell dumpsys package com.oplus.ipemanager
adb shell pm list permissions -f -g        # find com.oplus.permission.safe.IOT -> protectionLevel
adb shell "su -c 'dmesg'" > research/local/pad3/dmesg.txt
adb logcat -d > research/local/pad3/logcat.txt
```

If `adb pull` of a protected file is refused on a rooted tablet, copy it to `/sdcard/Download/`, pull it, and delete the copy straight away. The tool does this automatically.
