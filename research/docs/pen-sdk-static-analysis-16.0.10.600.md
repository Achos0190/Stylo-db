# Pen SDK static analysis: OxygenOS 16.0.10.600 (Pad 3, global)

Date: 2026-11-07.

**Source:** official OnePlus full OTA `OPD2415_16.0.10.600(EX01)` GLO (record `src-rom-opd2415-16-0-10-600`). The files were extracted from the ROM and decompiled with JADX 1.5.6 in `research/local/`, which git ignores. This page records findings only, never code.

**Status:** everything here is **inferred**. It comes from the ROM and has not yet been read on the owner's tablet (`CLAUDE.md`). The database records hold the details.

## Components

| Package | Version | Location in ROM | Role |
|---|---|---|---|
| `com.oplus.ipemanager` | 16.13.0 | `my_product/priv-app/IPeManager` | Pen service: `btadsorb.CoreService`, process `:ble` |
| `com.oneplus.note` | 16.7.0 | `my_stock/del-app/OppoNote2` | First-party client. Holds the IOT permission and binds `PENCIL_SDK` directly |
| `com.coloros.ocs.opencapabilityservice` | 16.0.8 | `my_stock/app/OpenCapabilityService` | Authenticates third-party apps for capability `IPE_PENCIL_CLIENT` |
| `oplus` (oplus-framework-res) | 8.0 | `system_ext/framework` | Defines `com.oplus.permission.safe.IOT` |
| `oplus_wireless_pen.ko` | (no version) | `vendor_dlkm/lib/modules` | CPS8601 wireless-charging and pairing driver |
| `cps8601_firmware.bin` | `33a4ab21…` | `odm/firmware/wireless_pen/24976/` | Charger-chip firmware. This is the only project folder in this ROM |

## Access path

```
First-party (Notes):  app ──bind PENCIL_SDK (holds safe.IOT)──▶ CoreService.onBind ──▶ ISdkAidlInterface binder
Third-party (HiPaint): app ──authCode──▶ OCS (IPE_PENCIL_CLIENT)
                                         │ verifies ECDSA authCode (package + cert digest + permits [+ expiry])
                                         │ binds PENCIL_SDK itself, sends txn 0xFFFFFF (PENCIL_CAPABILITY auth report)
                                         └─▶ returns ipemanager's SDK binder to the app (CapabilityInfo)
Ordinary app:          bind PENCIL_SDK ──✗ refused by the platform: safe.IOT is signature|privileged (0x12)
```

- **There is no per-call caller check** in the SDK binder or in `onBind`. The gates are the bind-time permission and OCS's hand-off.
- **The `authCode` is an offline Oplus signature tied to one app identity.** Only Oplus can issue one. Reusing another app's code is outside this project's rules.
- **The legitimate third-party route is an `IPE_PENCIL_CLIENT` authorisation from OnePlus/Oplus** (Route C).

## Haptic call chain (ipemanager 16.13.0)

| Txn | Method | Implementation |
|---|---|---|
| 4 | `setVibrationType(int)` | BLE GATT characteristic write: template with byte[3] = type and byte[4] = level+1. Only when the pen is connected (state 2) |
| 5 | `startVibration(int)` | Stylo 2 (second-generation pencil) only. Calls an internal routine that is not yet traced |
| 11 | `startFeedBackVibration()` | `OplusTouchNodeManager.writeNodeFileByDevice(0, 38, "3")`: **touch-panel node 38, not BLE** |
| 12 | `stopFeedBackVibration()` | the same node, value `"2"` |

The other codes are 1 setISdkAidlCallback, 2 getVibrationSwitchState, 3 getPencilConnectState, 6 getSuportSdkVersion (returns 20100), 7 getSupportFeatures, 8 isDemoModeEnable, 9 isFunctionVibrationEnable, 10 unsetISdkAidlCallback and 13 isNewFeatureSupported. **This differs from the repo's reconstruction for codes 1, 3, 4, 5, 7, 8 and 9.** The PoC's codes stay unchanged until the owner reviews this.

**Working hypothesis:** writing feedback is switched on and off through the touch controller (the digitiser link to the pen), while BLE sets the pattern and strength.

## Pen feature config (`my_stock/etc/pencil_feature_config.xml`)

- "OnePlus Stylo 2" is an alias of "OPPO Pencil 2 Pro".
- Its features are `stylus_type_vibration`, `laser_pen`, `slide_to_copy_paste` (pen firmware ≥ `5357.01.00.13` / `4D45.03.00.12`), `global_writing_sound_switch` and `stylus_type_capacitive_film`.
- `marker_pen_vibration` is listed only for the Pencil 3 Pro (Stylo Pro).

## HiPaint client (6.4.2) — added 2026-11-08

An approved third-party app, inspected to confirm the access model from the client side. APK obtained by the owner from APKMirror (the only mirror `CLAUDE.md` allows), `com.aige.hipaint` versionName 6.4.2 (code 251), SHA-256 `8300e029…a60296`, signer `CN=huion` (cert SHA-256 `57e4c186…90879`, apksigner verifies, v3 scheme). Full JADX decompile (42,374 Java files). This is `inferred`, not device-observed.

- HiPaint bundles the **OCS base SDK** (`com.oplus.ocs.base.*`: `AuthenticateCustomerBuilder`, `IServiceBroker`, `IAuthenticationListener`, `AuthResult`, `CapabilityInfo`) and the **pen SDK** (`com.oplus.ipemanager.sdk.*`).
- It requests capability **`IPE_PENCIL_CLIENT`** and binds `com.coloros.opencapabilityservice` / `OpenAuthenticateService` through `IServiceBroker.handleAuthentication`, receiving an `AuthResult` (permit bits) and a `CapabilityInfo` that carries ipemanager's SDK binder — **Route C exactly as modelled**.
- Error constants `ERROR_CODE_AUTHENTICATION_EXPIRED` and `commitRegister reject, incomplete credential` confirm the authCode is **identity-bound and expirable**, matching `utils/m.java`. The credential is tied to **HiPaint's own huion certificate**, so an interoperable app would need its own Oplus-issued authCode for its own package + cert.
- **No** reference to `OplusTouchNodeManager` / `writeNodeFileByDevice`: HiPaint calls only the SDK; the privileged touch-node write stays server-side in ipemanager.
- **authCode location:** declared in HiPaint's **AndroidManifest** as meta-data `com.oplus.ocs.ipepencil.AUTH_CODE` — a **79-byte** token (`0x01` version + DER ECDSA P-256 signature `30 44 …` + 8-byte permit/expiry trailer). OCS reads it from the **caller's** manifest (`getApplicationInfo(pkg, GET_META_DATA)` in `authenticate/utils/h.java`) and verifies the ECDSA against on-device Oplus keys (`utils/m.java`); carried as `AuthCodeInfo{authCode, enabled, expireIn}`. Client classes: `h8/f` (client name), `h8/h` (`checkAuthResult`: success = errorCode `1001`, client ver `10000`, brand-gated), `h8/e` (SDK wrapper), `l6/d` (`IServiceBroker` proxy, OCS lib `1.0.11`). The token is bound to huion's package + cert; **not** reusable, so interop needs an Oplus-issued `AUTH_CODE` for our own package + cert in our manifest.

## Touch node 38 trace — added 2026-11-08

From the ROM framework jars (`system/framework/oplus-framework.jar`, `oplus-services.jar`), decompiled from the 16.0.10.600 system image. `inferred`.

**Full write path for node 38:**
```
app ─ OplusTouchNodeManager.writeNodeFileByDevice(0,38,"3")   [oplus-framework.jar, @hide; forwards only, no check]
    └─▶ OplusActivityTaskManager binder
        └─▶ ColorTouchNodeManagerService.writeNodeFileByDevice  [system_server, com.android.server.wm]
            └─▶ IOplusTouch HAL  vendor.oplus.hardware.touch.IOplusTouch/default .touchWriteNodeFile(0,38,…)
                └─▶ sysfs node (path mapped inside the vendor HAL, not in the jars)
```

- **The write is signature-gated.** The binder entry point is `OplusActivityTaskManagerServiceEnhance.writeNodeFileByDevice`, which calls `enforceCallingPermission("oplus.permission.OPLUS_COMPONENT_SAFE", …)` **before** delegating to `ColorTouchNodeManagerService` (whose inner impl has no check — so reading only that inner method is misleading; the Enhance wrapper is what the binder hits).
- **`oplus.permission.OPLUS_COMPONENT_SAFE` = `protectionLevel 0x2` (signature)**, declared in `oplus-framework-res.apk` (package `oplus`). Only platform/oplus-signed apps hold it; an ordinary app cannot. So node 38 is **signature-walled — the same class of barrier as the IOT/SDK bind**.
- **SELinux is _not_ the barrier.** `activity_task_service` is an `app_api_service` and `allow untrusted_app_all app_api_service (service_manager (find))`, so an ordinary app *can* reach the binder — and the `@hide` API is reflectable — but it is then stopped by the signature permission.
- **Node 38 is internal** — not among the public `OplusTouchNodeManager` constants (which end at 22); the vendor HAL maps it to a sysfs path.
- **Conclusion:** there is **no simpler non-OCS path** for the feedback haptic. An ordinary third-party app cannot write node 38. ipemanager works because it is platform-signed and holds `OPLUS_COMPONENT_SAFE`. **Route C (OCS authCode) remains the only third-party route.**

> **Correction note (2026-11-08):** an earlier draft of this section claimed "no caller gate" and floated node 38 as a candidate simpler route. That was wrong — it was based on reading only the inner `ColorTouchNodeManagerService` impl and missing the `…ServiceEnhance` permission check. The signature gate above is the corrected finding.

## BLE command table (Task 4) — `ipemanager` `o3/j.java`, `l0.java`

Pen commands are GATT writes to characteristic `f2707l0`, all prefixed `0x2C`:

| Command | Bytes | Notes |
|---|---|---|
| `startVibration` (`l0.F0`) | `2C 92 01 02 FF 02` (`f5605p`) | fixed; gated by `isFunctionVibrationEnable` + pen connected |
| `setVibrationType(type)` | `2C 94 02 <type> <level+1>` (`f5606q`) | `byte[3]`=type, `byte[4]`=level+1 |
| `sendInstructionToPencil` | `2C 90 01 01 01` / `…00` (`f5603n`/`f5604o`) | toggled by `settings_enable_oppo_pencil` |

Pencil-type map (`f5615z`): **2 = "OnePlus Pencil 2 Pro" (Stylo 2)**, 5 = Pencil 2, 6 = Stylo 2R, etc. BLE names (`f5590a`): "Iceland/Ivy Pencil". These are recorded as constants; replaying them to a pen is a device action needing owner approval and only with bytes captured from Notes (`CLAUDE.md`).

## Next

1. **On the tablet:** confirm on the device's own build the IOT protection level, `OPLUS_COMPONENT_SAFE`'s level, the ipemanager version and the transaction codes (`pad3_readout.py`).
2. **SELinux is already answered** from the ROM (above): reachability isn't the barrier; the signature permission is.
