# Stylus attach handshake and the regional-ROM stylus fix

How a OnePlus tablet first recognises a Stylo pen, why that breaks after converting a Chinese ColorOS tablet to a global OxygenOS ROM, and what the community fixes change.

This is not the haptics path. It matters for haptics because nothing works on the pen until this handshake succeeds: no pairing, no pressure over Bluetooth, no vibration.

Status labels follow the research database: **verified** (checked against the source named), **inferred** (reasoned from verified facts), **unverified** (reported, not checked), **open** (unknown).

## Summary

- The tablet has a wireless-charging chip for the pen, the **CPS8601**, driven by the kernel module `oplus_wireless_pen.ko` ("Wireless Pen Charger Cps8601", "QTI Glink wireless pen driver"). *Verified (module info quoted in the source repo).*
- When the pen snaps on, the chip detects it and receives packets from the pen over the charging link. One packet carries the **pen's Bluetooth LE address**, which the driver reports onward. *Verified (kernel log in the source repo).*
- The chip runs firmware loaded from `/odm/firmware/wireless_pen/<number>/cps8601_firmware.bin`. Chinese and global ROMs ship these files under **different directory numbers**. *Verified (source repo, crDroid file list).*
- On converted tablets the expected directory is missing, the handshake never completes, and the pen never pairs. Supplying the missing directories, or the Chinese ROM's whole `odm.img`, restores the pen. *Verified as the source author's finding; user results are mixed.*

## Devices

| Device | Codename | Notes |
| --- | --- | --- |
| OnePlus Pad Pro (China) | caihong | Target of the source module. *Verified* |
| OnePlus Pad 2 (global) | rainbow | Target of the source module. *Verified* |
| OnePlus Pad 3 / OPPO Pad 4 Pro (OPD2415 / OPD2413) | — | Named in the XDA module thread and in user reports. Not checked against its own ROM. *Unverified* |
| OnePlus Stylo 2 / OnePlus Smart Stylus Pro (China name) | — | The pen. *Verified* |

The module info quoted below is from the Pad Pro / Pad 2 kernel (`6.1.68-android14`). The Pad 3 may ship a different driver version.

## Components

### Kernel driver

```
filename:     /vendor/lib/modules/oplus_wireless_pen.ko
description:  Wireless Pen Charger Cps8601
description:  QTI Glink wireless pen driver
alias:        i2c:cps-wls-charger
depends:      panel_event_notifier,oplus_bsp_boot_projectinfo,pmic_glink
vermagic:     6.1.68-android14-11-o-g2ba5638f614f SMP preempt mod_unload modversions aarch64
license:      GPL v2
```

- `i2c:cps-wls-charger`: the chip sits on an I²C bus.
- `pmic_glink`: the driver talks through Qualcomm's GLINK link to the power-management firmware.
- `oplus_bsp_boot_projectinfo`: the driver depends on Oplus's project-information module, which identifies the hardware variant. This supports the directory-number explanation below. *Inferred*
- `panel_event_notifier`: the driver reacts to display on/off events. *Inferred from the name*
- GPL v2 licence: OnePlus should publish the source for this module in its kernel releases. That would answer how the directory is chosen. *Open*

### Chip

- Named CPS8601 in the driver. The `cps` prefix suggests ConvenientPower Systems, a wireless-power chip maker. *Unverified*
- The source author describes it as a Qi 1.3 wireless charging chip. *Unverified*
- The firmware strings show it acting as the **charging transmitter**. It enters TX mode, sends Qi pings, runs foreign-object detection from power loss, calibrates its coil, and logs receiver removal. *Verified (strings, see below)*

### Firmware files

From the source module (`odm/firmware/wireless_pen/`):

| Directory | Size | SHA-256 | Added |
| --- | --- | --- | --- |
| `23926` | 15,360 B | `cfac6737…400f9e81` | 6 Mar 2025 (original commit) |
| `23927` | 15,360 B | `cfac6737…400f9e81` | 6 Mar 2025 |
| `23976` | 15,360 B | `cfac6737…400f9e81` | 6 Mar 2025 |
| `23978` | 15,360 B | `cfac6737…400f9e81` | 6 Mar 2025 |
| `24926` | 15,360 B | `33a4ab21…6dd6aa1ee4` | 12 Apr 2026 ("Add new firmware to fix it", contributor FRTI64) |

- The four `239xx` copies are byte-identical. `24926` is a different build: 9,675 bytes differ, and it fills slightly more of the 15 KB image (15,065 bytes of code and data, against 14,972). *Verified*
- The crDroid device tree for caihong lists the same layout: real files in `23976` and `23978`, with `23926` and `23927` created as symlinks to them. *Verified*

  ```
  odm/firmware/wireless_pen/23976/cps8601_firmware.bin;SYMLINK=odm/firmware/wireless_pen/23926/cps8601_firmware.bin
  odm/firmware/wireless_pen/23978/cps8601_firmware.bin;SYMLINK=odm/firmware/wireless_pen/23927/cps8601_firmware.bin
  ```

- The directory numbers look like Oplus project numbers, the internal IDs for each hardware variant. `239xx` would then be 2023 projects (Pad Pro and Pad 2) and `249xx` a 2024 project (likely the Pad 3 generation). *Inferred*

### What is inside the firmware

The image looks like code for an 8051-family microcontroller. *Inferred from the evidence below.*

- The file opens with long-jump instructions (`02 xx xx`) at offsets `0x0000`, `0x0003` and `0x000B`. These are the reset, external-interrupt-0 and timer-0 vector slots of an 8051.
- The format strings use `%bu`, the byte-sized integer format of the Keil C51 compiler.

Readable strings, the same in both builds:

| Area | Strings |
| --- | --- |
| Transmit and ping | `cmd enter tx mode`, `Ping:Freq=%u,Duty=%u,VIN=%u,VPA=%u,IPA=%u`, `fre=%u;duty=%u;` |
| Power and foreign-object detection | `RP=%u;RPpower=%u;Papower=%u;Vpa=%u;Ipa=%u;Vpk=%u`, `ploss_rx=%d ploss_tx=%d`, `ploss=%d,ploss_th=%d`, `ploss fod trigger`, `dlp!maxpower %d`, `###ovpk %d %d` |
| Coil calibration | `q cali`, `save Q value successful`, `save Q value fail`, `cail cnt:%d cail width:%d …`, `cail_times %bu`, `adcq :` |
| State and control | `RX remove`, `low power`, `int_enable=%u, int_type=%u`, `clear int_l=%bu`, `clear int_h=%bu`, `start crc check`, `sys_reset` |
| Factory mode | `cmd enter equipent mode`, `exit equipment mode`, `enter idcfg` |
| Version | `ver%bu.%bu` (version printed at run time, not stored as text) |

There is nothing about haptics, vibration or Bluetooth in the chip firmware. It handles power transfer and the low-level packets the pen sends back over the charging link. *Verified (strings)*

## Attach handshake

From the kernel log the source author captured while attaching and removing the pen on a Chinese ROM:

```
[cps8601]:irq_flag = 0x200
[cps8601]:Rx attach!
[cps8601]:irq_flag = 0x5
[cps8601]:tx wakeup!
[cps8601]:[error_attach_check_work_func] count 1
[cps8601]:irq_flag = 0x8
[cps8601]:irq_flag = 0x10
...
[cps8601]:[cps_wls_get_ask_packet]get add_check
[cps8601]:irq_flag = 0x20
[cps8601]:receive ask pkt!
[cps8601]:[cps_wls_get_ask_packet]get ble addr(956)
[cps8601]:cps8601_valid_check: .. .. ..
[cps8601]:[cps_wls_get_ask_packet]report 1
```

Read as a sequence (*inferred*):

1. The pen lands on the magnetic charger and the chip raises an attach interrupt (`Rx attach!`).
2. The chip wakes and starts transmitting power (`tx wakeup!`). The driver watches for a failed attach.
3. The pen talks back by modulating the charging load. Qi calls this ASK (amplitude-shift keying) communication; the log's `receive ask pkt!` is one such packet.
4. One packet carries the pen's Bluetooth LE address (`get ble addr`). The driver validates it (`cps8601_valid_check`) and reports it (`report 1`), presumably to the accessory service so it can pair over Bluetooth LE without user action.
5. From here, pen traffic, including haptic commands from `com.oplus.ipemanager`, goes over Bluetooth LE.

This is why the stylus seems dead after a conversion. If the chip has no matching firmware, step 3 never produces a valid address and the pen never pairs.

## Root cause on converted tablets

1. The source author compared the two ROMs and found the `wireless_pen` directory names differ. *Verified*
2. Swapping in the Chinese ROM's `odm.img` made the pen connect. *Verified (author's result)*
3. The author concluded the first handshake needs firmware the global ROM doesn't provide under the expected directory. *Verified (author's conclusion)*
4. The driver probably picks the directory from the hardware's project number. A Chinese-hardware tablet running a global ODM image then looks for a directory that isn't there. *Inferred*

The XDA module thread's author describes the same cause: the pen firmware is the same across regions, only the loading path differs. *Unverified (from search excerpts; the thread itself could not be read)*

## Fixes in circulation

| Approach | What it changes | Reported results |
| --- | --- | --- |
| **Firmware overlay module** (cxzlw's module; jjjhitel's XDA module appears similar) | Adds `odm/firmware/wireless_pen/<number>/cps8601_firmware.bin` for every known number so the driver finds a file whatever the variant | Author: pen works. One XDA reply: the KernelSU module did not work. *Unverified* |
| **Swap `odm.img`** | Flashes the Chinese ROM's whole ODM partition over the global ROM | One report: works (ColorOS .501 ODM on OxygenOS .500 EU). Another: tablet crashes on boot. A third: pen stopped charging after a later global update. *Unverified* |

Things to know before choosing:

- **Partition overlay depends on the root manager.** The source module keeps its files under `odm/` at the module root. Whether that is mounted over `/odm` depends on the root manager (Magisk, KernelSU) and its version. Check that the files appear under `/odm/firmware/wireless_pen/` after reboot. *Open*
- **Swapping `odm.img` changes far more than pen firmware.** The ODM partition holds many vendor-specific files, which is a plausible reason for the boot failures. It also has to be redone after each system update. *Inferred*
- **Firmware versions matter.** `24926` was added in April 2026 because the older file stopped being enough for some setup. A module built before then may lack the directory your hardware needs. *Inferred from the commit*
- Keep a full backup and a way back to stock before flashing partitions.

## Checking a tablet (read-only)

| Check | Command | What to look for |
| --- | --- | --- |
| Shipped directories | `adb shell ls /odm/firmware/wireless_pen/` | Which numbers the current ROM provides |
| Files actually mounted | `adb shell ls -l /odm/firmware/wireless_pen/*/` | After installing a module, its directories should appear here |
| Driver loaded | `adb shell lsmod \| grep oplus_wireless_pen` (root may be needed) | The module is present |
| Handshake log | `adb shell su -c 'dmesg \| grep cps8601'`, then attach the pen | `Rx attach!`, `receive ask pkt!`, `get ble addr` and `report 1` mean success; stopping at `error_attach_check_work_func` suggests a failed handshake |
| Hardware variant | `adb shell getprop \| grep -i prjname` | The project number to compare with the directory names *(property name not confirmed)* |
| Firmware identity | `sha256sum` of each `cps8601_firmware.bin` | Compare with the table above |

## Relevance to Stylo 2 haptics

- **Separate paths.** The CPS8601 handles charging, attach detection and handing over the pen's Bluetooth address. Haptic commands go from apps to `com.oplus.ipemanager`, then over Bluetooth LE to the pen. The chip firmware has no haptics content. *Verified (strings) and inferred (path)*
- **It is still a prerequisite.** On a converted Pad 3 (like the one this project was tested on), haptics can only work after this handshake succeeds.
- **It gives a starting point for capture.** The address hand-over marks the moment pairing starts, so an HCI snoop log taken while attaching the pen should show the Bluetooth LE connection to the accessory service being set up.

## Open questions

- How does `oplus_wireless_pen` choose the firmware directory? The GPL source in OnePlus's kernel release should show it.
- Which numbers belong to which hardware, and which number does the Pad 3 / OPD2413 / OPD2415 use?
- What changed between the `239xx` firmware and `24926`? The `ver%bu.%bu` print in the kernel log would show the version.
- Does the jjjhitel XDA module match the cxzlw module? The XDA thread could not be read automatically.

## Sources

- cxzlw, [oplus-global-pen-enabler](https://github.com/cxzlw/oplus-global-pen-enabler): README (module info, kernel logs, method), `module.prop`, firmware files. Read at commit `1869c22`, 2 Nov 2026.
- crDroid, [android_device_oneplus_caihong `proprietary-files.txt`](https://github.com/crdroidandroid/android_device_oneplus_caihong/blob/16.0/proprietary-files.txt), "Wireless pen firmware" section.
- XDA (not readable automatically; search excerpts only): [Stylus Fix for OnePlus on Different Regional ROM](https://xdaforums.com/t/magisk-module-stylus-fix-for-oneplus-on-different-regional-rom.4753007/), [Found the way to fix stylus for OnePlus Pad 3 COS to OOS](https://xdaforums.com/t/found-the-way-to-fix-stylus-for-oneplus-pad-3-cos-to-oos.4749818/), [Stylus not recognized on OPPO Pad 4 Pro after converting to OPD2415](https://xdaforums.com/t/title-q-stylus-not-recognized-on-oppo-pad-4-pro-opd2413-after-converting-to-oneplus-pad-3-global-rom-opd2415.4780653/).
