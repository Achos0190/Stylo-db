package io.github.yeetingwaterbottle.stylo2.haptics

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import android.content.res.ColorStateList
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.oplus.ipemanager.sdk.Vibration
import io.github.yeetingwaterbottle.stylo2.haptics.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var client: StylusVibrationClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        client = StylusVibrationClient(applicationContext)

        // Dropdown: list vibration presets
        val presets = Vibration.values().map { it.name.lowercase().replaceFirstChar(Char::titlecase) }
        binding.dropdownVibration.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, presets)
        )
        binding.dropdownVibration.setOnItemClickListener { _, _, pos, _ ->
            val chosen = Vibration.values()[pos]
            client.setVibrationType(chosen)
        }

        // Bind / Unbind
        binding.btnBind.setOnClickListener { client.bind() }
        binding.btnUnbind.setOnClickListener { client.unbind() }

        // Enable/disable switch starts/stops vibration
        binding.switchEnable.setOnCheckedChangeListener { _, checked ->
            val ok = if (checked) client.startVibration() else client.stopVibration()
            if (!ok) {
                // Failure here likely means not connected; show a small hint
                Snackbar.make(binding.root, "Operation failed (not connected?)", Snackbar.LENGTH_SHORT).show()
                binding.switchEnable.isChecked = false
            }
        }

        // Observe connection state + error and reflect in UI
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                client.connection.collect { c ->
                    val (state, err) = c

                    // Chip text & enabled state for controls
                    binding.chipStatus.text = when (state) {
                        StylusVibrationClient.State.DISCONNECTED -> "Disconnected"
                        StylusVibrationClient.State.CONNECTING -> "Connecting…"
                        StylusVibrationClient.State.CONNECTED_SDK -> "Connected"
                        StylusVibrationClient.State.CONNECTED_WRONG_IFACE -> "Wrong interface"
                        StylusVibrationClient.State.ERROR -> when (err) {
                            StylusVibrationClient.SdkError.PermissionDenied -> "Permission denied"
                            StylusVibrationClient.SdkError.ServiceUnavailable -> "Service unavailable"
                            StylusVibrationClient.SdkError.NotConnected -> "Not connected"
                            is StylusVibrationClient.SdkError.WrongInterface -> "Wrong interface"
                            is StylusVibrationClient.SdkError.Remote -> "Remote error"
                            is StylusVibrationClient.SdkError.Unknown -> "Error"
                            null -> "Error"
                        }
                    }

                    binding.chipStatus.applyStatusColors(state, err)

                    val connected = state == StylusVibrationClient.State.CONNECTED_SDK
                    binding.dropdownVibration.isEnabled = connected
                    binding.switchEnable.isEnabled = connected

                    // Show specific, user-facing explanations only when it changes to ERROR
                    if (state == StylusVibrationClient.State.ERROR && err != null) {
                        val message = when (err) {
                            StylusVibrationClient.SdkError.PermissionDenied ->
                                "Permission denied by system. This app likely lacks the required IOT permission."
                            StylusVibrationClient.SdkError.ServiceUnavailable ->
                                "Pencil SDK service not found or not available on this device."
                            is StylusVibrationClient.SdkError.WrongInterface ->
                                "Incompatible SDK interface (${err.found ?: "unknown"})."
                            StylusVibrationClient.SdkError.NotConnected ->
                                "Not connected to the SDK. Tap Bind first."
                            is StylusVibrationClient.SdkError.Remote ->
                                "Remote call failed: ${err.cause?.message ?: "unknown"}"
                            is StylusVibrationClient.SdkError.Unknown ->
                                "Unexpected error: ${err.cause?.message ?: "unknown"}"
                        }
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }

                    // Reset the switch if we lost connection
                    if (!connected && binding.switchEnable.isChecked) {
                        binding.switchEnable.isChecked = false
                    }
                }
            }
        }

    }

    private fun Chip.applyStatusColors(
        state: StylusVibrationClient.State,
        err: StylusVibrationClient.SdkError?
    ) {
        val (bgAttr, fgAttr) = when {
            state == StylusVibrationClient.State.CONNECTED_SDK ->
                com.google.android.material.R.attr.colorPrimaryContainer to
                        com.google.android.material.R.attr.colorOnPrimaryContainer
            state == StylusVibrationClient.State.CONNECTING ->
                com.google.android.material.R.attr.colorSecondaryContainer to
                        com.google.android.material.R.attr.colorOnSecondaryContainer
            state == StylusVibrationClient.State.CONNECTED_WRONG_IFACE ->
                com.google.android.material.R.attr.colorTertiaryContainer to
                        com.google.android.material.R.attr.colorOnTertiaryContainer
            state == StylusVibrationClient.State.ERROR &&
                    err is StylusVibrationClient.SdkError.PermissionDenied ->
                com.google.android.material.R.attr.colorErrorContainer to
                        com.google.android.material.R.attr.colorOnErrorContainer
            state == StylusVibrationClient.State.ERROR ->
                com.google.android.material.R.attr.colorErrorContainer to
                        com.google.android.material.R.attr.colorOnErrorContainer
            else -> // DISCONNECTED
                com.google.android.material.R.attr.colorSurfaceVariant to
                        com.google.android.material.R.attr.colorOnSurface
        }

        val bg = MaterialColors.getColor(this, bgAttr)
        val fg = MaterialColors.getColor(this, fgAttr)
        chipBackgroundColor = ColorStateList.valueOf(bg)
        setTextColor(fg)
    }

}
