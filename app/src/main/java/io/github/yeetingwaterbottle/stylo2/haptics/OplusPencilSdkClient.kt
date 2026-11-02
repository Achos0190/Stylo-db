package io.github.yeetingwaterbottle.stylo2.haptics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.oplus.ipemanager.sdk.ISdkAidlInterface
import com.oplus.ipemanager.sdk.Vibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * StylusVibrationClient
 *
 * - No UI (no Toast/Snackbar) inside this class.
 * - Exposes a single StateFlow<Connection> you can observe in the UI.
 * - Differentiates permission errors vs. bind failures vs. wrong interface.
 */
class StylusVibrationClient(private val appContext: Context) {

    companion object {
        private const val TAG = "Stylo2Haptics"
        private const val ACTION_PENCIL_SDK = "com.oplus.ipemanager.ACTION.PENCIL_SDK"
        private const val PKG_IPE = "com.oplus.ipemanager"
        private const val DESC_SDK = "com.oplus.ipemanager.sdk.ISdkAidlInterface"
        private const val PERM_IOT = "com.oplus.permission.safe.IOT"
    }

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED_SDK,
        CONNECTED_WRONG_IFACE,
        ERROR
    }

    /** Structured error the UI can render nicely. */
    sealed class SdkError {
        object PermissionDenied : SdkError()
        object ServiceUnavailable : SdkError()               // bindService returned false / service missing
        data class WrongInterface(val found: String?) : SdkError()
        object NotConnected : SdkError()
        data class Remote(val cause: Throwable?) : SdkError()
        data class Unknown(val cause: Throwable?) : SdkError()
    }

    /** UI observes this to get both state and (optional) error detail. */
    data class Connection(val state: State, val error: SdkError? = null)

    private val _connection = MutableStateFlow(Connection(State.DISCONNECTED, null))
    val connection: StateFlow<Connection> = _connection

    private var sdk: ISdkAidlInterface? = null
    private var isBound: Boolean = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            runCatching {
                val desc = binder.interfaceDescriptor
                Log.i(TAG, "onServiceConnected: $name, binder=$binder, iface=$desc")
                if (desc == DESC_SDK) {
                    sdk = ISdkAidlInterface.Stub.asInterface(binder)
                    _connection.value = Connection(State.CONNECTED_SDK, null)
                } else {
                    sdk = null
                    _connection.value = Connection(
                        State.CONNECTED_WRONG_IFACE,
                        SdkError.WrongInterface(desc)
                    )
                }
            }.onFailure { t ->
                Log.e(TAG, "onServiceConnected error", t)
                sdk = null
                _connection.value = Connection(State.ERROR, SdkError.Unknown(t))
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.w(TAG, "onServiceDisconnected: $name")
            sdk = null
            isBound = false
            _connection.value = Connection(State.DISCONNECTED, null)
        }
    }

    /** Bind to the Pencil SDK service. Safe to call multiple times. */
    fun bind() {
        if (isBound) return

        // 1) Preflight permission check (signature perms will appear DENIED if app lacks them).
        val hasIot = ContextCompat.checkSelfPermission(appContext, PERM_IOT) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasIot) {
            Log.w(TAG, "Missing permission: $PERM_IOT")
            _connection.value = Connection(State.ERROR, SdkError.PermissionDenied)
            return
        }

        // 2) Try to bind
        _connection.value = Connection(State.CONNECTING, null)
        val intent = Intent(ACTION_PENCIL_SDK).setPackage(PKG_IPE)
        Log.d(TAG, "bind(): action=$ACTION_PENCIL_SDK pkg=$PKG_IPE")

        try {
            val ok = appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "bindService returned: $ok")
            if (!ok) {
                _connection.value = Connection(State.ERROR, SdkError.ServiceUnavailable)
                return
            }
            isBound = true
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException on bind", se)
            _connection.value = Connection(State.ERROR, SdkError.PermissionDenied)
        } catch (t: Throwable) {
            Log.e(TAG, "bind error", t)
            _connection.value = Connection(State.ERROR, SdkError.Unknown(t))
        }
    }

    /** Unbind if currently bound. */
    fun unbind() {
        if (!isBound) return
        runCatching { appContext.unbindService(conn) }
            .onFailure { Log.w(TAG, "unbind() error", it) }
        stopVibration();
        isBound = false
        sdk = null
        _connection.value = Connection(State.DISCONNECTED, null)
    }

    fun setVibrationType(type: Vibration): Boolean {
        val api = sdk ?: return failNotConnected("setVibrationType")
        return runCatching {
            api.setVibrationType(type.ordinal)
            Log.d(TAG, "setVibrationType -> $type")
            true
        }.onFailure { Log.e(TAG, "setVibrationType($type) failed", it) }
            .getOrDefault(false)
    }

    fun setVibrationType(typeName: String): Boolean {
        val enumVal = runCatching { Vibration.valueOf(typeName.uppercase()) }.getOrNull()
        return enumVal?.let { setVibrationType(it) } ?: false.also {
            Log.w(TAG, "Unknown vibration type: $typeName")
        }
    }

    fun startVibration(): Boolean {
        val api = sdk ?: return failNotConnected("startVibration")
        return runCatching {
            api.startFeedBackVibration()
            true
        }.onFailure { Log.e(TAG, "startFeedBackVibration failed", it) }
            .getOrDefault(false)
    }

    fun stopVibration(): Boolean {
        val api = sdk ?: return failNotConnected("stopVibration")
        return runCatching {
            api.stopFeedBackVibration()
            true
        }.onFailure { Log.e(TAG, "stopFeedBackVibration failed", it) }
            .getOrDefault(false)
    }

    private fun failNotConnected(what: String): Boolean {
        Log.w(TAG, "$what: not connected")
        _connection.value = Connection(State.ERROR, SdkError.NotConnected)
        return false
    }

    /** Optional diagnostics, kept from your original class. */
    fun logEnvironmentOnce() {
        val ai = runCatching {
            appContext.packageManager.getApplicationInfo(appContext.packageName, 0)
        }.getOrNull()
        val apkPath = ai?.publicSourceDir
        val isPriv = apkPath?.startsWith("/system/priv-app/") == true

        Log.i(TAG, "sdkInt=${android.os.Build.VERSION.SDK_INT} " +
                "device=${android.os.Build.DEVICE}/${android.os.Build.PRODUCT} " +
                "model=${android.os.Build.MODEL}")
        Log.i(TAG, "APK path=$apkPath (priv-app=$isPriv)")

        val iot = ContextCompat.checkSelfPermission(appContext, PERM_IOT)
        Log.i(TAG, "checkPermission($PERM_IOT) => " +
                if (iot == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED")
    }
}
