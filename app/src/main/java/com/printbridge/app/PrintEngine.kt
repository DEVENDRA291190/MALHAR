package com.printbridge.app

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object PrintEngine {
    private fun notConnected(ctx: Context) = IllegalStateException(ctx.getString(R.string.st_not_connected))

    suspend fun send(ctx: Context, s: PrintSettings, bytes: ByteArray): Result<Unit> {
        val c = PrinterLink.create(ctx, s) ?: return Result.failure(notConnected(ctx))
        return try {
            c.open().getOrThrow()
            c.write(bytes).getOrThrow()
            delay(500)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            c.close()
        }
    }

    suspend fun printPdf(
        ctx: Context,
        file: File,
        s: PrintSettings,
        onPage: (Int, Int) -> Unit,
    ): Result<Unit> {
        val c = PrinterLink.create(ctx, s) ?: return Result.failure(notConnected(ctx))
        var pfd: ParcelFileDescriptor? = null
        return try {
            c.open().getOrThrow()
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = try {
                PdfRenderer(pfd)
            } catch (e: SecurityException) {
                throw IllegalStateException(ctx.getString(R.string.st_pdf_password))
            }
            try {
                val n = r.pageCount
                check(n > 0) { ctx.getString(R.string.st_pdf_empty) }
                for (i in 0 until n) {
                    onPage(i + 1, n)
                    val bytes = withContext(Dispatchers.Default) { TsplBuilder.pdfPage(r, i, s) }
                    c.write(bytes).getOrThrow()
                }
            } finally {
                r.close()
            }
            delay(500)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
            c.close()
        }
    }
}
