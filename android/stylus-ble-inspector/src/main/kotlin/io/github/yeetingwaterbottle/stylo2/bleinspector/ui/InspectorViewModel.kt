package io.github.yeetingwaterbottle.stylo2.bleinspector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yeetingwaterbottle.stylo2.bleinspector.ble.BleScanner
import io.github.yeetingwaterbottle.stylo2.bleinspector.ble.GattInspector
import io.github.yeetingwaterbottle.stylo2.bleinspector.ble.HexCodec
import io.github.yeetingwaterbottle.stylo2.bleinspector.model.GattCharacteristicInfo
import io.github.yeetingwaterbottle.stylo2.bleinspector.model.ScannedDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class InspectorViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = BleScanner(app)
    val inspector = GattInspector(app)

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _bonded = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val bonded: StateFlow<List<ScannedDevice>> = _bonded.asStateFlow()

    private var scanJob: Job? = null

    /** Enumerate already-bonded devices (the pen won't show in a scan while connected). */
    fun refreshBonded() {
        _bonded.value = runCatching { scanner.bondedDevices() }.getOrDefault(emptyList())
    }

    fun startScan() {
        if (_scanning.value) return
        _scanError.value = null
        _devices.value = emptyList()
        _scanning.value = true
        scanJob = viewModelScope.launch {
            scanner.scan()
                .catch { _scanError.value = it.message; _scanning.value = false }
                .collect { _devices.value = it }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _scanning.value = false
    }

    fun connect(address: String) {
        stopScan()
        viewModelScope.launch { inspector.connect(address) }
    }

    fun discover() = viewModelScope.launch { inspector.discover() }

    /** Enable notifications on all notify/indicate characteristics (handshake-style). */
    fun subscribeAll() = viewModelScope.launch { inspector.subscribeAllNotify() }

    fun read(info: GattCharacteristicInfo) = viewModelScope.launch { inspector.read(info) }

    fun write(info: GattCharacteristicInfo, hexInput: String, withResponse: Boolean) =
        viewModelScope.launch {
            val bytes = HexCodec.parseHex(hexInput)
            if (bytes == null) {
                inspector.note("write", "invalid hex: \"$hexInput\"")
                return@launch
            }
            inspector.write(info, bytes, withResponse)
        }

    fun setNotify(info: GattCharacteristicInfo, enable: Boolean) =
        viewModelScope.launch { inspector.setNotify(info, enable) }

    fun disconnect() = inspector.disconnect()

    /**
     * Self-driving haptic test: connect to the bonded Stylo 2, discover, then write the known
     * ipemanager command bytes to the vendor control characteristic 58480001 (subscribing to its
     * notify first). Every step is logged (mirrored to logcat tag StyloInsp). Triggerable over adb.
     */
    fun runHapticTest() = viewModelScope.launch {
        val svcU = "58480000-5a6a-1122-3300-004095112200"
        val chU = "58480001-5a6a-1122-3300-004095112200"
        inspector.note("autotest", "=== haptic auto-test start ===")
        refreshBonded()
        val pen = bonded.value.firstOrNull { it.name?.contains("stylo", ignoreCase = true) == true }
        if (pen == null) {
            inspector.note("autotest", "FAIL: no bonded device whose name contains 'stylo'")
            return@launch
        }
        inspector.note("autotest", "connecting ${pen.address}")
        val res = inspector.connect(pen.address)
        if (res.isFailure) {
            inspector.note("autotest", "FAIL: connect: ${res.exceptionOrNull()?.message}")
            return@launch
        }
        delay(700)
        inspector.discover()
        delay(2000)
        val info = inspector.services.value
            .firstOrNull { it.uuid.equals(svcU, ignoreCase = true) }
            ?.characteristics?.firstOrNull { it.uuid.equals(chU, ignoreCase = true) }
        if (info == null) {
            inspector.note("autotest", "FAIL: characteristic 58480001 not found after discovery")
            return@launch
        }
        inspector.note("autotest", "target OK: service 58480000 / char 58480001 (${info.properties.summary()})")
        // Handshake-faithful: enable notifications on ALL characteristics (as ipemanager does), then
        // give the pen a moment to send any init/ask packets (watch the live Pen→phone feed).
        inspector.subscribeAllNotify()
        inspector.note("autotest", "all notifies enabled; waiting for pen init packets (2s)…")
        delay(2000)
        // Writes use Write-Request (with response) — matching ipemanager's default write type.
        val seq = listOf(
            "enable" to "2C 90 01 01 01",
            "startVib" to "2C 92 01 02 FF 02",
            "vib t0" to "2C 94 02 00 01",
            "vib t1" to "2C 94 02 01 01",
            "vib t2" to "2C 94 02 02 01",
            "vib t3" to "2C 94 02 03 01",
            "startVib#2" to "2C 92 01 02 FF 02",
        )
        for ((label, hex) in seq) {
            val bytes = HexCodec.parseHex(hex) ?: continue
            inspector.note("autotest", ">>> $label : $hex (with response)")
            inspector.write(info, bytes, true)
            delay(1600)
        }
        inspector.note("autotest", "commands sent; capturing pen notifications (4s)…")
        delay(4000)
        inspector.note("autotest", "=== haptic auto-test done ===")
    }

    override fun onCleared() {
        stopScan()
        inspector.disconnect()
    }
}
