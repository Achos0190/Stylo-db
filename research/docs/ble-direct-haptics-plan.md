# Route B — direct BLE to the pen: capture & test plan

Date: 2026-11-08. Chosen path forward: **talk to the pen directly over BLE, bypassing the tablet's pen framework**, as an ordinary app. Hard constraint from the owner (and `CLAUDE.md`): **it must not interfere with the system's existing pen link or OnePlus Notes.**

This consolidates the DB plans `plan-hci-snoop-capture` and `plan-ble-independent-access-test` with the 16.13.0 static findings. Everything here is a plan; nothing below has been run on a device.

## Why this route
The pen is a BLE peripheral. ipemanager's `setVibrationType` / `startVibration` are plain GATT writes to one characteristic. An ordinary app holding only **`BLUETOOTH_CONNECT`** (a normal runtime permission) could, in principle, write the same bytes — no signature permission, no OCS authCode, no root. The signature walls (`safe.IOT`, `OPLUS_COMPONENT_SAFE`) all sit in the *tablet framework*; going straight to the pen skips them.

## What static analysis already told us (the targets)
From `ipemanager` `g0.java` / `l0.java` / `o3/j.java` (host side; to be confirmed by snoop):

- **One control characteristic**, all commands prefixed `0x2C`.
- **`setVibrationType`** → `2C 94 02 <type> <level+1>` (byte[3]=type 0–3, byte[4]=level+1).
- **`startVibration`** → `2C 92 01 02 FF 02` (fixed discrete buzz).
- **Enable/session** → `sendInstructionToPencil` writes `2C 90 01 01 01` (on) / `…00` (off), and all writes are gated on **pen connection state == 2** — so there is probably a session/enable step.
- **Pen BLE names** for the scan: `Iceland Pencil`, `Iceland-O Pencil`, `Ivy Pencil`, `Ivy-O Pencil`. Pencil-type **2 = Stylo 2**.

**Scope note:** this route reaches the **configurable / discrete vibration** (mechanism B). The **writing-feedback texture** (`startFeedBackVibration`) is **not** BLE — it's touch node 38 via the vendor HAL, signature-walled — so it will **not** appear in the snoop and is **out of reach** for an unprivileged app. Decide if mechanism B is the goal; if the drawing-time texture is required, that's Route C / Shizuku / privileged instead.

## Phased plan (each phase gated by non-interference)

### Phase 0 — passive HCI snoop (zero interference) · `plan-hci-snoop-capture`
The snoop sits *below* the stack and only logs the system's own traffic; it opens no connection of ours.
1. Owner enables the Bluetooth HCI snoop log (Developer Options; a settings change → **ask first**). Optionally BlueShark+Shizuku for live markers.
2. Drive the pen **only through Notes** (Notes is the oracle; never modify it). Run the 9 captures — crucially **capture 6/7** (feedback on/off) and **capture 9** (fresh connect, to catch any `2C 90` enable/handshake).
3. Pull `btsnoop_hci` into `research/local/pad3/snoop/` (git-ignored; redact BT addresses). In Wireshark filter `btatt`.
4. **Confirm against the static targets:** the control characteristic's UUID/handle, that the writes match `2C 94…` / `2C 92…`, whether the link is **encrypted** (if so, an OTA sniff shows ciphertext — the snoop is the right tool), and whether a `2C 90` enable precedes the first haptic.

Go/no-go: if **no** haptic-correlated ATT write exists, direct BLE is off the table for that haptic — record it and fall back. (We expect writes to exist for mechanism B; none for node 38.)

### Phase 1 — read-only GATT discovery (minimal interference) · `plan-ble-independent-access-test`, `stylus-ble-inspector`
Our own app, **discovery only, no writes.**
1. Scan with `BLUETOOTH_CONNECT`/nearby-devices; find the pen by the names above (pen stays bonded to the system — we do **not** re-bond or forget it).
2. Connect a **separate** GATT client (`autoConnect=false`, `TRANSPORT_LE`); dump the GATT tree; confirm the `0x2C`-control characteristic + its sibling notify. Export (redact addresses).
3. **Non-interference gate:** with our client connected, draw in Notes in split-screen. Do haptics still fire? Does the system link drop (`dumpsys bluetooth_manager`)? **If anything degrades, disconnect and STOP** → `gap-second-gatt-client-disruption`. This answers whether the pen even accepts a second concurrent client (`gap-second-gatt-client-access`).

### Phase 2 — captured-byte replay (controlled, owner present) 
Only if Phases 0–1 are clean.
1. Write **only the bytes captured from Notes in Phase 0** (per `CLAUDE.md`: captured values, **no blind fuzzing** — the static bytes are what to expect, not what to send raw). If Phase 0 showed a `2C 90` enable is required, replay that first.
2. Ground truth: **does the pen physically vibrate?** (`gap-captured-write-buzzes-pen` — "works" only means it buzzed.)
3. Throughout: if pen input or Notes breaks, disconnect our client and report.

## Non-interference safeguards (standing)
- Never unbond / forget / re-pair the pen — the system owns that bond.
- Separate GATT client only; short-lived connections; `close()` promptly.
- Don't aggressively change MTU / PHY / connection priority.
- Always validate Notes + stylus input still work *during* and *after* each step.
- One variable per step; stop at the first sign of disruption.

## Open questions this plan closes
| Gap | Question | Closed by |
|---|---|---|
| `gap-second-gatt-client-access` | Can a non-system app connect while the pen stays system-bonded? | Phase 1.2 |
| `gap-second-gatt-client-disruption` | Does our connection break Notes / the system link? | Phase 1.3 |
| `gap-session-handshake` | Is a `2C 90` enable / session needed first? | Phase 0 cap 9 |
| `gap-haptic-command-bytes` | Exact on-the-wire bytes | Phase 0 |
| `gap-captured-write-buzzes-pen` | **Go/no-go:** does a replayed write buzz the pen? | Phase 2 |

## Assets in the repo
- `android/stylus-ble-inspector` — the ordinary-app GATT client (scan/connect/discover/write/export).
- `stylo2_ble::recon::candidate_control_characteristics`, `Stylo2GattProfile` — seams the characteristic/bytes fill.
- `research/tools/pad3_readout.py` — read-out + evidence recording.

## Status
All of the above is **inferred/planned**. No device step has run. The first on-device action (enabling the snoop log) is a settings change and needs the owner's explicit go-ahead.
