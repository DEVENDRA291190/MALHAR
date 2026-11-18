package com.printbridge.app

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Lets Chrome / Android's Print dialog print straight to the selected USB or Bluetooth printer. */
class LabelPrintService : PrintService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession =
        object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                val s = SettingsStore(this@LabelPrintService).load()
                val id = generatePrinterId("printbridge")
                val wMils = (s.w / 25.4 * 1000).toInt()
                val hMils = (s.page / 25.4 * 1000).toInt()
                val media = PrintAttributes.MediaSize("pb_media", "${s.w} x ${s.page} mm", wMils, hMils)
                val caps = PrinterCapabilitiesInfo.Builder(id)
                    .addMediaSize(media, true)
                    .addResolution(PrintAttributes.Resolution("r203", "203 dpi", 203, 203), true)
                    .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()
                val name = if (s.printerName.isEmpty()) getString(R.string.svc_setup_first)
                else "PrintBridge · ${s.printerName}"
                val info = PrinterInfo.Builder(id, name, PrinterInfo.STATUS_IDLE)
                    .setCapabilities(caps)
                    .build()
                addPrinters(listOf(info))
            }

            override fun onStopPrinterDiscovery() {}
            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {}
            override fun onStartPrinterStateTracking(printerId: PrinterId) {}
            override fun onStopPrinterStateTracking(printerId: PrinterId) {}
            override fun onDestroy() {}
        }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        printJob.start()
        scope.launch {
            val file = File(cacheDir, "svc_job.pdf")
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    val pfd = printJob.document.data ?: error("No document")
                    java.io.FileInputStream(pfd.fileDescriptor).use { i ->
                        file.outputStream().use { o -> i.copyTo(o) }
                    }
                    pfd.close()
                }
            }
            if (copied.isFailure) {
                printJob.fail(copied.exceptionOrNull()?.message ?: "Cannot read document")
                return@launch
            }
            val s = SettingsStore(this@LabelPrintService).load()
            PrintEngine.printPdf(this@LabelPrintService, file, s) { _, _ -> }
                .onSuccess { printJob.complete() }
                .onFailure { printJob.fail(it.message ?: "Print failed") }
        }
    }
}
