package com.printbridge.app

import android.app.Application
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val settings: PrintSettings,
    val printers: List<PrinterEntry> = emptyList(),
    val status: String = "",
)

@Suppress("DEPRECATION")
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val _ui = MutableStateFlow(UiState(store.load()))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val usbDevices = mutableMapOf<String, UsbDevice>()
    private val permAction = "com.printbridge.app.USB_PERMISSION"

    private fun str(id: Int, vararg a: Any): String = getApplication<Application>().getString(id, *a)
    private fun setStatus(s: String) = _ui.update { it.copy(status = s) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action != permAction) return
            if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                setStatus(str(R.string.st_usb_granted))
            else setStatus(str(R.string.st_usb_denied))
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(permAction), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refresh()
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(receiver)
    }

    fun update(f: (PrintSettings) -> PrintSettings) {
        val n = f(_ui.value.settings)
        store.save(n)
        _ui.update { it.copy(settings = n) }
    }

    fun setTransport(t: Transport) {
        update { it.copy(transport = t, printerId = "", printerName = "") }
        refresh()
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        val list = when (_ui.value.settings.transport) {
            Transport.USB -> {
                val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
                usbDevices.clear()
                mgr.deviceList.values.map { d ->
                    val id = PrinterLink.usbId(d)
                    usbDevices[id] = d
                    PrinterEntry(id, "${d.productName ?: "USB printer"} (${id.removePrefix("usb:")})", Transport.USB)
                }
            }
            Transport.BLUETOOTH -> try {
                val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                bm.adapter?.bondedDevices.orEmpty().map { d ->
                    PrinterEntry("bt:${d.address}", "${d.name ?: "Bluetooth"} (${d.address})", Transport.BLUETOOTH)
                }
            } catch (_: SecurityException) {
                setStatus(str(R.string.st_bt_denied))
                emptyList()
            }
        }
        _ui.update { it.copy(printers = list) }
    }

    fun select(p: PrinterEntry) {
        update { it.copy(printerId = p.id, printerName = p.name) }
        if (p.transport == Transport.USB) {
            val ctx = getApplication<Application>()
            val dev = usbDevices[p.id] ?: return
            val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            if (!mgr.hasPermission(dev)) {
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pi = PendingIntent.getBroadcast(
                    ctx, 0, Intent(permAction).setPackage(ctx.packageName), flags
                )
                mgr.requestPermission(dev, pi)
            }
        }
    }

    private fun send(build: (PrintSettings) -> ByteArray) {
        val s = _ui.value.settings
        if (s.printerId.isEmpty()) return setStatus(str(R.string.st_select))
        setStatus(str(R.string.st_sending))
        viewModelScope.launch {
            PrintEngine.send(getApplication(), s, build(s))
                .onSuccess { setStatus(str(R.string.st_sent)) }
                .onFailure { setStatus(str(R.string.st_error, it.message ?: "?")) }
        }
    }

    fun testPrint() = send { TsplBuilder.test(it) }

    fun calibrate() {
        if (_ui.value.settings.paper == PaperType.RECEIPT) setStatus(str(R.string.st_no_calibration))
        else send { TsplBuilder.calibrate(it) }
    }
}
