# Pickup prompt: local session with the Pad 3

Instructions for a Claude Code session running on the computer the OnePlus Pad 3 is plugged into. It continues the research track in `research/`: it reads the pen firmware, driver and attach handshake off the tablet and records them.

This is separate from the root [`PICKUP.md`](../../PICKUP.md), which covers on-device haptics testing (Routes A and B). Don't do that work in this session unless the owner asks.

## Before starting (for the person)

1. Get the repository and the research branch:
   ```powershell
   git clone https://github.com/Achos0190/Stylo-db.git
   cd Stylo-db
   git checkout ccr-1974d431-9lzw9a
   ```
   If the research branch has already been merged, stay on `main` instead.
2. Connect the tablet with USB debugging on, and confirm `adb devices` lists it as `device` (not `unauthorized`).
3. Decide whether root reads are allowed. Steps marked **(root)** need `su` on the tablet and are skipped unless you say yes. `PICKUP.md` excludes root for the implementation work; this read-out only reads.
4. Start Claude Code in the repository and paste one of:

   > Read `research/docs/local-session-pickup.md` and follow it. Root reads are not allowed. Ask me before anything that changes the tablet.

   > Read `research/docs/local-session-pickup.md` and follow it. Root reads are allowed for the steps marked (root). Ask me before anything that changes the tablet.

---

## For Claude: context

**The project.** A Magisk module and companion app that turn on OnePlus Stylo 2 haptics in every app. The app binds to OnePlus's pen service `com.oplus.ipemanager` (action `com.oplus.ipemanager.ACTION.PENCIL_SDK`, permission `com.oplus.permission.safe.IOT`) and calls `setVibrationType` and `startFeedBackVibration`. See the root `README.md` and `app/src/main/java/`.

**The research so far** is in `research/` of `Achos0190/Stylo-db`, added on branch `ccr-1974d431-9lzw9a`. Read `research/README.md` first for the layout, the data rules, and how this folder relates to the rest of Stylo-db (`stylo2/`, `android/`, `PICKUP.md`). In short:

- `research/data/*.json`: 78 records, one file per type. Each carries a status (`verified`, `unverified`, `inferred`, `open`), a basis and an updated date.
- `research/tools/validate.py` checks the data, `build.py` assembles it, and `fwinfo.py` inspects pen firmware files. All three are standard-library Python. On Windows, run them as `py -3 research/tools/...` (or `python`).
- `research/docs/pen-attach-handshake.md`: the main write-up this session extends.
- `research/reference/`: the jjhitel module scripts and the cxzlw firmware hash manifest.

**What is already established** (details in the write-up):

- Haptic commands go from apps to `com.oplus.ipemanager`, then over Bluetooth LE to the pen.
- Before that can happen, the pen's magnetic charger chip pairs it. That chip is the CPS8601, driven by the kernel module `oplus_wireless_pen.ko`. When the pen attaches, the chip receives the pen's Bluetooth LE address over the charging link.
- The chip loads `/odm/firmware/wireless_pen/<ro.boot.prjname>/cps8601_firmware.bin`. After a Chinese-to-global ROM conversion the matching folder is missing, so the pen never pairs. Community modules fix this by copying firmware into a folder named after the tablet's project number.
- Known project numbers: `23926`, `23927`, `23976`, `23978` (OnePlus Pad Pro / Pad 2) and `24926`, `24976` (OnePlus Pad 2 Pro). The Pad 2 Pro appears to be the same hardware family as the Pad 3, so this tablet probably reports one of the last two. **Unconfirmed: this session should settle it.**
- Known firmware builds, by SHA-256:
  - `cfac6737…400f9e81`: the `239xx` folders.
  - `33a4ab21…6dd6aa1ee4`: `24926` in cxzlw's module.
  - No `24976` copy has been examined.
- The firmware is 8051-style code for wireless charging, with nothing about haptics.

**This tablet** was converted from OPD2413 (Chinese ColorOS) to OPD2415 (global OxygenOS) on Android 16. A stylus fix may already be installed; find out, don't assume.

## Ground rules

1. **Read-only on the tablet.**
   - Allowed: `getprop`, `ls`, `cat` and `adb pull` of readable files, `dumpsys`, `pm path`, `dmesg`, `logcat -d`, `modinfo`, `lsmod`, `sha256sum`, `find`.
   - Not allowed: flashing, writing to partitions, remounting, installing, removing or disabling modules or apps, changing settings, `setenforce`, rebooting into bootloader, recovery or EDL, or clearing logs (`dmesg -c`, `logcat -c`).
   - Ask the person first before anything that changes the tablet, including turning on the Bluetooth HCI snoop log.
   - Copying a file to the tablet's `/sdcard/Download/` so it can be pulled is fine. Delete the copy afterwards.
2. **Root is opt-in.** Run steps marked **(root)** only if the owner said root reads are allowed. Then use root only for reads, as `adb shell "su -c '<command>'"`. Otherwise skip those steps and list them as not done in your report. Never install or change anything to get root.
3. **Keep pulled files out of git.**
   - Firmware, kernel modules, APKs, full `getprop` dumps and logs go in `research/local/pad3/`, which git ignores.
   - Commit only hashes, sizes, short string excerpts and findings.
4. **Redact personal identifiers before committing.** That means Bluetooth addresses (the value in `get ble addr(...)`), serial numbers (`ro.serialno`, `ro.boot.serialno`), IMEIs and account names. Write `<redacted>` instead.
5. **Stay within scope.**
   - Don't decompile or patch OnePlus apps.
   - Don't work on getting around signature, permission or app-authentication checks.
   - Don't change the app code unless the person asks.
6. **Git:** commit to the research branch `ccr-1974d431-9lzw9a`, or to a new branch off `main` if that one has been merged. Never commit directly to `main`, and don't force-push.
7. **Status labels:**
   - Mark what you read from the tablet `verified`, with a basis like "Read from the Pad 3 (OPD2415, build …) on <date>".
   - Mark conclusions `inferred`.
8. **Other tracks:** if a finding confirms or contradicts something in `stylo2/docs/` (especially `I-evidence-ledger.md` or `C-protocol.md`), say so in your report. Edit those documents only if the owner asks.

## Tasks

### 0. Check the setup

```powershell
git status
git branch --show-current          # ccr-1974d431-9lzw9a
py -3 research/tools/validate.py   # should print OK
adb devices
New-Item -ItemType Directory -Force research/local/pad3 | Out-Null
```

### 1. Identify the tablet

```powershell
adb shell getprop ro.boot.prjname
adb shell getprop ro.product.model
adb shell getprop ro.product.device
adb shell getprop ro.product.system.manufacturer
adb shell getprop ro.build.display.id
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop > research/local/pad3/getprop.txt
Select-String -Path research/local/pad3/getprop.txt -Pattern "prjname|region|regionmark|hw_|nv_id|oplus.version|ota"
```

Record:
- the project number;
- the model and device;
- the build;
- any property that names the hardware region or operator, as a candidate for telling Chinese from global project numbers.

### 2. Firmware layout, overlays and fix modules

Without root:

```powershell
adb shell ls -lR /odm/firmware/wireless_pen/
adb shell cat /proc/mounts | Select-String "anyfs|wireless_pen|/odm"
```

**(root)**, for installed fix modules and the overlay contents:

```powershell
adb shell "su -c 'ls /data/adb/modules'"
adb shell "su -c 'cat /data/adb/modules/*/module.prop'"
adb shell "su -c 'ls -lR /dev/anyfs/upper/odm/firmware/wireless_pen 2>/dev/null'"
```

Work out:
- which folders the ODM image ships;
- which ones, if any, a module adds (jjhitel's overlay keeps its copies under `/dev/anyfs/upper/`, and `/proc/mounts` shows an `anyfs` overlay on `/odm/firmware`);
- whether any folder is a symlink;
- whether the folder named after `ro.boot.prjname` exists.

Without root you can't list modules directly. An `anyfs` overlay on `/odm/firmware`, or a folder that matches `ro.boot.prjname` but isn't in the stock ODM image, is indirect evidence of a fix.

### 3. Pull and inspect the firmware

```powershell
adb pull /odm/firmware/wireless_pen research/local/pad3/wireless_pen
```

If that's refused, then **(root)**: copy to shared storage, pull, and delete the copy. Without root, record that the files couldn't be read and move on.

```powershell
adb shell "su -c 'cp -r /odm/firmware/wireless_pen /sdcard/Download/wireless_pen'"
adb pull /sdcard/Download/wireless_pen research/local/pad3/wireless_pen
adb shell "su -c 'rm -r /sdcard/Download/wireless_pen'"
```

Then run:

```powershell
py -3 research/tools/fwinfo.py research/local/pad3/wireless_pen
py -3 research/tools/fwinfo.py --diff <file-a> <file-b>     # for each pair of distinct builds
py -3 research/tools/fwinfo.py --strings <file>             # for any unknown build
```

Record for each folder: size, used bytes, SHA-256, and whether it matches a known build. In particular:
- Is there a `24976` build? How does it compare with `24926`?
- Has any string changed from the known set?

### 4. Driver

Find the module file. Without root, try the usual locations:

```powershell
adb shell ls -l /vendor/lib/modules/oplus_wireless_pen.ko /vendor_dlkm/lib/modules/oplus_wireless_pen.ko
```

**(root)**, if it isn't there or you need the loaded-module view:

```powershell
adb shell "su -c 'find /vendor /vendor_dlkm /system_dlkm /odm -name oplus_wireless_pen.ko 2>/dev/null'"
adb shell "su -c 'lsmod'" | Select-String "wireless_pen"
adb shell "su -c 'modinfo <path found above>'"
```

Pull the module into `research/local/pad3/` (don't commit it). Its strings include the same fields `modinfo` prints, plus the firmware path format, so this step works without root if the file is readable:

```powershell
adb pull <path found above> research/local/pad3/oplus_wireless_pen.ko
py -3 research/tools/fwinfo.py --strings research/local/pad3/oplus_wireless_pen.ko | Select-String "description=|alias=|depends=|vermagic=|license="
py -3 research/tools/fwinfo.py --strings research/local/pad3/oplus_wireless_pen.ko | Select-String "wireless_pen|cps8601|firmware|prj|project|%s|%d"
```

Record the description, alias, dependencies and `vermagic`. The Pad 3 kernel is expected to differ from the Pad Pro's `6.1.68-android14`.

A format string such as `wireless_pen/%s/cps8601_firmware.bin`, together with what fills `%s`, would answer `gap-pen-firmware-directory`. Quote only the relevant strings in findings.

### 5. Attach handshake log

With the pen off the tablet, ask the person to attach it, wait a few seconds, then remove it and attach it again. Then read the app-level log (no root needed):

```powershell
adb logcat -d > research/local/pad3/logcat.txt
Select-String -Path research/local/pad3/logcat.txt -Pattern "ipemanager|cps8601|wireless_pen|Pencil|stylus" | Out-File research/local/pad3/logcat-pen.txt
```

**(root)**, for the kernel log, which has the `[cps8601]` lines:

```powershell
adb shell "su -c 'dmesg'" > research/local/pad3/dmesg.txt
Select-String -Path research/local/pad3/dmesg.txt -Pattern "cps8601|wireless_pen|firmware" | Out-File research/local/pad3/dmesg-pen.txt
```

Compare the sequence with the one in the write-up (`Rx attach!`, `tx wakeup!`, `receive ask pkt!`, `get ble addr`, `report 1`). Also look for:
- firmware-load lines that show the path requested;
- any version line;
- what `com.oplus.ipemanager` logs when the address arrives.

Redact Bluetooth addresses before quoting anything.

### 6. Pen service package

```powershell
adb shell pm path com.oplus.ipemanager
adb shell dumpsys package com.oplus.ipemanager > research/local/pad3/ipemanager.txt
Select-String -Path research/local/pad3/ipemanager.txt -Pattern "versionName|versionCode|PENCIL_SDK|CoreService|safe.IOT|permission" | Select-Object -First 60
```

Confirm or correct against `if-oplus-ipemanager`:
- the service component `.btadsorb.CoreService`;
- the action `com.oplus.ipemanager.ACTION.PENCIL_SDK`;
- the permission `com.oplus.permission.safe.IOT` and its protection level, if shown.

Record the installed version.

### 7. Record the results

Edit `research/data/`. Set `updated` to today on every record you change.

- `devices.json` → `dev-oneplus-pad3`: project number, model, build, firmware folders and builds, installed fix module if any.
- `interfaces.json`:
  - `if-cps8601-firmware`: new builds and hashes, and which folder holds which.
  - `if-oplus-wireless-pen-driver`: Pad 3 module info and the path format, if found.
  - `if-oplus-ipemanager`: installed version and confirmed names.
- `gaps.json` → `gap-pen-firmware-directory`: answer or narrow it. Close other gaps only with direct evidence.
- `sources.json`: add `src-pad3-readout`, a record of this read-out (what was read and when; no personal identifiers).
- Add `research/reference/pad3-firmware/MANIFEST.md`, in the same style as the cxzlw manifest: paths, sizes, hashes, no binaries.
- `research/docs/pen-attach-handshake.md`: fill in the Pad 3 row, the firmware table and the handshake section, and upgrade statuses where the evidence now supports it.

Then:

```powershell
py -3 research/tools/validate.py
py -3 research/tools/build.py
git status        # make sure nothing under research/local/ or research/build/ is staged
git add research
git commit -m "Record Pad 3 pen firmware, driver and handshake read-out"
git push origin ccr-1974d431-9lzw9a
```

If this session has the Artifact and ArtifactData tools, also update the database page (https://claude.ai/artifact/HrYP4b6GswQ7hTPEWmzTKY):
1. List its `records` collection to get each document's current version.
2. Write only the changed records, from `research/build/seed/<id>.json`, each pinned with `if_version`.

Without those tools, list the changed record IDs in your report so the cloud session can sync them.

### 8. Report back

Give the person a short summary:
- the project number and build;
- the firmware folders and which build each holds;
- whether a fix module or overlay is active;
- the driver version and the firmware path format, if found;
- how the handshake went;
- the `com.oplus.ipemanager` version;
- what was committed;
- what is still open.

## Not in this session

- **Bluetooth HCI snoop capture** of a pen attach and a writing session in OnePlus Notes. This needs the person to turn on the snoop log in Developer options, so ask first. It shows what goes over Bluetooth LE when haptics play (`gap-oneplus-transport`).
- **Two app fixes**, done only if the person asks:
  - `unbind()` calls `stopVibration()` after `unbindService()`; stop first.
  - `getSupportFeatureList()` has no transaction code.

## PowerShell notes

- Wrap root commands in double quotes on the outside and single quotes inside: `adb shell "su -c 'ls /odm'"`.
- `Select-String` replaces `grep`. `Out-File` writes UTF-16 by default in Windows PowerShell 5; add `-Encoding utf8` if a tool needs to read the file.
- `Get-FileHash -Algorithm SHA256 <file>` gives the same hash as `sha256sum` and `fwinfo.py`.
