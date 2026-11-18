package com.printbridge.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface PrinterConnection {
    suspend fun open(): Result<Unit>
    suspend fun write(data: ByteArray): Result<Unit>
    fun close()
}

class UsbPrinterConnection(
    private val ctx: Context,
    private val device: UsbDevice,
) : PrinterConnection {
    private var conn: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var out: UsbEndpoint? = null

    override suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            check(mgr.hasPermission(device)) { "USB permission not granted" }
            var foundIf: UsbInterface? = null
            var foundEp: UsbEndpoint? = null
            loop@ for (i in 0 until device.interfaceCount) {
                val itf = device.getInterface(i)
                for (e in 0 until itf.endpointCount) {
                    val ep = itf.getEndpoint(e)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == UsbConstants.USB_DIR_OUT
                    ) {
                        foundIf = itf; foundEp = ep
                        break@loop
                    }
                }
            }
            val itf = foundIf ?: error("No bulk OUT endpoint on this device")
            val ep = foundEp ?: error("No bulk OUT endpoint on this device")
            val c = mgr.openDevice(device) ?: error("Cannot open USB device")
            check(c.claimInterface(itf, true)) { "Cannot claim USB interface" }
            conn = c; iface = itf; out = ep
        }
    }

    override suspend fun write(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val c = conn ?: error("Not connected")
            val ep = out ?: error("Not connected")
            var off = 0
            val chunk = 16384
            while (off < data.size) {
                val len = minOf(chunk, data.size - off)
                val n = c.bulkTransfer(ep, data, off, len, 5000)
                check(n > 0) { "USB write failed" }
                off += n
            }
        }
    }

    override fun close() {
        try { iface?.let { conn?.releaseInterface(it) } } catch (_: Exception) {}
        try { conn?.close() } catch (_: Exception) {}
        conn = null; iface = null; out = null
    }
}

@SuppressLint("MissingPermission")
class BluetoothPrinterConnection(private val device: BluetoothDevice) : PrinterConnection {
    private var socket: BluetoothSocket? = null
    private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = device.createRfcommSocketToServiceRecord(sppUuid)
            s.connect()
            socket = s
        }
    }

    override suspend fun write(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = socket ?: error("Not connected")
            s.outputStream.write(data)
            s.outputStream.flush()
        }
    }

    override fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}

object PrinterLink {
    fun usbId(d: UsbDevice) = "usb:%04X:%04X".format(d.vendorId, d.productId)

    @SuppressLint("MissingPermission")
    fun create(ctx: Context, s: PrintSettings): PrinterConnection? {
        if (s.printerId.isEmpty()) return null
        return if (s.transport == Transport.USB) {
            val mgr = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            mgr.deviceList.values.firstOrNull { usbId(it) == s.printerId }
                ?.let { UsbPrinterConnection(ctx, it) }
        } else {
            try {
                val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
                bm.adapter?.getRemoteDevice(s.printerId.removePrefix("bt:"))
                    ?.let { BluetoothPrinterConnection(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}
