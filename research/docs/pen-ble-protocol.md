# Stylo 2 BLE protocol — preemptive research (ipemanager 16.13.0)

Date: 2026-11-08. Static analysis of `com.oplus.ipemanager` (`btadsorb.ble` package: `l0`, `g`, `e`, `g0`, `o3/j`), to prepare the next direct-BLE test. GATT UUIDs below are **device-confirmed** (inspector discovery on the Pad 3); the protocol semantics are **inferred** from decompiled code.

## 1. GATT profile

| Role (ipemanager name) | Service UUID | Key characteristic |
|---|---|---|
| **INTERFACE_COM** (commands) | `58480000-5a6a-1122-3300-004095112200` | **`58480001-…`** — WRITE\|WRITE_NR\|NOTIFY — the command + notify channel (`l0.f2707l0`) |
| Nordic UART (NUS) | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | `6e400002` WRITE, `6e400003`/`6e400004` NOTIFY (role TBD — likely firmware/log) |
| Sensitivity, Double-click, Battery, Charge-state, Log | `180932da…`, `18093398…`, `180938ac…`, `1809396a…`, `180937bc…` (`-2a69-11ec-8d3d-0242ac130003`) | each has a NOTIFY characteristic + CCCD |
| Device info | `0000180a-…` (SIG) | model/firmware strings |

All vendor commands and the control/notify traffic use the **one** characteristic `58480001`.

## 2. Command frame (`o3/j.java`)

Every command is a GATT write to `58480001` of the form:

```
2C <cmdId> <len> <payload…>
```

`0x2C` is the fixed header. Known command IDs:

| cmdId | const | bytes | meaning |
|---|---|---|---|
| `0x90` | f5603n / f5604o | `2C 90 01 01 01` / `…00` | **pencil enable / disable** (`sendInstructionToPencil`, gated by `settings_enable_oppo_pencil`) |
| `0x92` | f5605p | `2C 92 01 02 FF 02` | **startVibration** (the BLE vibration trigger; Stylo 2 only) |
| `0x94` | f5606q | `2C 94 02 <type> <level+1>` | **setVibrationType** (pattern + strength) |
| `0x93`/`0x95`/`0x96`/`0x97`/`0x98`/`0x99`/`0x9A` | f5608s… | `2C 9x …` | other settings (laser, slide-copy, sensitivity, etc. — not yet all mapped) |
| `0x26` (=38) | f5595f/g/h | `2C 26 01 [0B/0D]` | feature/mode (note: 38 here is a BLE cmd id, unrelated to touch node 38) |
| `0x01`,`0x06`,`0x08`,`0x25`,`0x38`,`0x39`,`0x53` | f5591b… | `2C xx …` | init / info / config commands used during the handshake |

## 3. Connect handshake — the piece we skipped

On `onConnectionStateChange(state=2)` → `discoverServices()` → `g.k()` (the discovery handler in `l0`), a **sequential, Handler-driven state machine** (`c1.a`, messages `1001`/`1007`) runs:

1. Enumerate services; assign each characteristic + its CCCD (`l0` ~2620–2720).
2. **Enable notifications on each characteristic one at a time** — for each: `setCharacteristicNotification(true)` then write the CCCD `ENABLE_NOTIFICATION_VALUE`, and **wait for `onDescriptorWrite`** before the next (steps ~300 ms apart). Order includes double-click, battery, charge-state, interface-com, etc.
3. After all notifies are up, **`doGattGetInfoComplete`** (state `100`) reads device info and runs follow-up commands (incl. `G0()`/`sendInstructionToPencil` → `2C 90 …` enable).
4. Incoming pen notifications are handled by **`g.e(characteristic)`** (and `f0()`), i.e. the pen talks back during init — the exact ask/response packets are **not yet traced** (next task).

**Our attempt 1 did:** connect → subscribe only `58480001` → write `2C 90/92/94…`. It **skipped the whole multi-characteristic notify-enable sequence and the info exchange**, so the pen was almost certainly not in its "ready" state.

## 4. Write type

No `setWriteType` anywhere in the BLE package → ipemanager writes with the characteristic **default = `WRITE_TYPE_DEFAULT` = Write Request (with response)**. **Our test used Write-Without-Response.** Fix: use with-response.

## 5. Vibration preconditions

- Pen connection state must be `2` (`g0.java` checks `m0.f2729g == 2`).
- `startVibration` (F0) also requires `isFunctionVibrationEnable` (`o3.t.m`).
- `settings_enable_oppo_pencil` must be set (the `2C 90 … 01` enable); it is **reset to 0 on every disconnect** (`e.java:73`), so it's per-session.
- **Transport split (important):** the *writing-feedback* haptic (`startFeedBackVibration`) is **touch node 38**, which is signature-walled — **not reachable over BLE**. Over BLE, `setVibrationType` only *configures*, and `startVibration` (`2C 92`) is the only *trigger*. If the physical buzz the owner wants is the writing-feedback texture, BLE alone may be unable to produce it.

## 6. Recipe for the next direct-BLE test

1. Connect to the bonded pen (by address) with **Write-Request** writes.
2. Discover, then **enable notifications on all vendor characteristics** (not just `58480001`), waiting for each `onDescriptorWrite`, mimicking the ordered sequence.
3. **Subscribe and log the pen's notifications** on `58480001` (and NUS `6e400003`) during/after connect — see whether the pen sends an "ask" packet expecting a reply.
4. Send `2C 90 01 01 01` (enable) **with response**, wait, then `2C 92 01 02 FF 02` (startVibration), then `2C 94 02 <type> <level+1>`.
5. If still no buzz with a faithful handshake, that is strong evidence the buzz requires the **touch-node (signature-walled) path**, i.e. not achievable by an unprivileged app over BLE — record that as the Route-B verdict.

## Open items
- Trace `g.e()` / `f0()` to decode the pen's notification packets and any required response (the true handshake).
- Map the remaining `2C 9x` command IDs.
- Determine the NUS service's role.
