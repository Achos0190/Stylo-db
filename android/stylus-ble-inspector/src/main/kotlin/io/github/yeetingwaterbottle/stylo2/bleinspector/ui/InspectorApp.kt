package io.github.yeetingwaterbottle.stylo2.bleinspector.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.yeetingwaterbottle.stylo2.bleinspector.ble.rememberBlePermissionState
import io.github.yeetingwaterbottle.stylo2.bleinspector.model.ConnState
import io.github.yeetingwaterbottle.stylo2.bleinspector.model.GattCharacteristicInfo
import io.github.yeetingwaterbottle.stylo2.bleinspector.model.ScannedDevice

/** One-tap command presets for the pen's control characteristic (from ipemanager static analysis). */
private val PEN_PRESETS = listOf(
    "startVib" to "2C 92 01 02 FF 02",
    "vib t0" to "2C 94 02 00 01",
    "vib t1" to "2C 94 02 01 01",
    "vib t2" to "2C 94 02 02 01",
    "vib t3" to "2C 94 02 03 01",
    "enable" to "2C 90 01 01 01",
)

@Composable
fun InspectorApp(vm: InspectorViewModel = viewModel(), autoTest: Boolean = false) {
    val perm = rememberBlePermissionState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!perm.granted) PermissionGate(perm.request) else InspectorScreen(vm)
        if (perm.granted && autoTest) {
            LaunchedEffect(Unit) { vm.runHapticTest() }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bluetooth permission needed", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "This tool scans for and connects to BLE devices to inspect their GATT services. " +
                "Grant nearby-devices access to continue.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Grant permission") }
    }
}

@Composable
private fun InspectorScreen(vm: InspectorViewModel) {
    val state by vm.inspector.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Stylo2 BLE Inspector", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Scan → connect → discover → read / write / subscribe. Candidate pens are flagged (heuristic).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        when (state) {
            ConnState.DISCONNECTED -> ScanPane(vm, Modifier.weight(1f))
            ConnState.CONNECTING -> ConnectingPane(vm)
            ConnState.CONNECTED -> DevicePane(vm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScanPane(vm: InspectorViewModel, modifier: Modifier) {
    val scanning by vm.scanning.collectAsState()
    val devices by vm.devices.collectAsState()
    val error by vm.scanError.collectAsState()
    val bonded by vm.bonded.collectAsState()
    LaunchedEffect(Unit) { vm.refreshBonded() }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (scanning) vm.stopScan() else vm.startScan() }) {
                Text(if (scanning) "Stop scan" else "Start scan")
            }
            Spacer(Modifier.width(12.dp))
            if (scanning) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("${devices.size} found")
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { vm.refreshBonded() }) { Text("Bonded (${bonded.size})") }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Scan error: $it", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            if (bonded.isNotEmpty()) {
                item {
                    Text(
                        "Bonded devices (connect directly — a connected pen won't advertise)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(bonded, key = { "bond:${it.address}" }) { d -> DeviceRow(d) { vm.connect(d.address) } }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "Scan results",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            items(devices, key = { "scan:${it.address}" }) { d -> DeviceRow(d) { vm.connect(d.address) } }
        }
    }
}

@Composable
private fun DeviceRow(d: ScannedDevice, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(d.name ?: "(no name)", style = MaterialTheme.typography.titleMedium)
                Text(if (d.bonded) "bonded" else "${d.rssi} dBm", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                d.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (d.isStylusCandidate) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "★ stylus candidate — ${d.candidateReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (d.serviceUuids.isNotEmpty()) {
                Text(
                    "services: ${d.serviceUuids.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConnectingPane(vm: InspectorViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Connecting to ${vm.inspector.connectedAddress ?: ""}…")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.disconnect() }) { Text("Cancel") }
    }
}

@Composable
private fun DevicePane(vm: InspectorViewModel, modifier: Modifier) {
    val services by vm.inspector.services.collectAsState()
    val logEntries by vm.inspector.log.collectAsState()
    val context = LocalContext.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Connected: ${vm.inspector.connectedAddress}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { vm.disconnect() }) { Text("Disconnect") }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = { vm.discover() }) { Text("Discover") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.subscribeAll() }) { Text("Subscribe all") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { shareReport(context, vm.inspector.buildReport()) }) {
                Text("Export")
            }
        }
        Spacer(Modifier.height(8.dp))
        val notifications by vm.inspector.notifications.collectAsState()
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    "Pen → phone (live) · ${notifications.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                val feed = remember(notifications) {
                    notifications.asReversed().take(80)
                        .joinToString("\n") { "${it.tag.take(8)}  ${it.message}" }
                }
                Text(
                    feed.ifEmpty { "(nothing yet — tap Subscribe all, then interact with the pen)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth().height(150.dp).verticalScroll(rememberScrollState()),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            if (services.isEmpty()) {
                item {
                    Text(
                        "No services yet — tap Discover.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            services.forEach { s ->
                item {
                    Text(
                        "service ${s.uuid}${if (s.primary) " (primary)" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(
                    s.characteristics,
                    key = { "${s.uuid}/${it.uuid}/${it.instanceId}" },
                ) { c -> CharacteristicRow(vm, c) }
            }
        }
        HorizontalDivider()
        Text(
            "Log",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        val logText = remember(logEntries) {
            logEntries.takeLast(200).joinToString("\n") { "[${it.tag}] ${it.message}" }
        }
        Text(
            logText.ifEmpty { "(empty)" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().height(180.dp).verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun CharacteristicRow(vm: InspectorViewModel, c: GattCharacteristicInfo) {
    var showWrite by remember { mutableStateOf(false) }
    var hexInput by remember { mutableStateOf("") }
    var withResponse by remember { mutableStateOf(true) }
    var subscribed by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(c.uuid, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Text(
                c.properties.summary(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                if (c.properties.read) {
                    TextButton(onClick = { vm.read(c) }) { Text("Read") }
                    Spacer(Modifier.width(4.dp))
                }
                if (c.properties.subscribable) {
                    TextButton(onClick = {
                        subscribed = !subscribed
                        vm.setNotify(c, subscribed)
                    }) { Text(if (subscribed) "Unsubscribe" else "Subscribe") }
                    Spacer(Modifier.width(4.dp))
                }
                if (c.properties.writable) {
                    TextButton(onClick = { showWrite = !showWrite }) { Text("Write…") }
                }
            }
            if (c.properties.writable) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "presets →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    PEN_PRESETS.forEach { (label, hex) ->
                        OutlinedButton(
                            onClick = { vm.write(c, hex, c.properties.write) },
                            modifier = Modifier.padding(end = 6.dp),
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (showWrite && c.properties.writable) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("hex bytes, e.g. 01 0A FF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.properties.write && c.properties.writeNoResponse) {
                        Checkbox(checked = withResponse, onCheckedChange = { withResponse = it })
                        Text("with response")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val useResponse = when {
                            c.properties.write && c.properties.writeNoResponse -> withResponse
                            c.properties.write -> true
                            else -> false
                        }
                        vm.write(c, hexInput, useResponse)
                    }) { Text("Send") }
                }
            }
        }
    }
}

private fun shareReport(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Stylo2 BLE findings")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "Export findings"))
}
