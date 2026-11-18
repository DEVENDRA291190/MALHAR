package com.printbridge.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer

/** TSPL commands (203 DPI, 8 dots per mm). */
object TsplBuilder {
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    private fun media(s: PrintSettings) = when (s.paper) {
        PaperType.RECEIPT -> "GAP 0 mm,0 mm"
        PaperType.GAP -> "GAP ${s.gap} mm,0 mm"
        PaperType.BLACK_MARK -> "BLINE ${s.mark} mm,0 mm"
    }

    private fun header(s: PrintSettings, hMm: Int) =
        "SIZE ${s.w} mm,$hMm mm\r\n${media(s)}\r\nSPEED ${s.speed}\r\n" +
            "DENSITY ${s.density.coerceIn(1, 15)}\r\nDIRECTION 1\r\n"

    fun test(s: PrintSettings): ByteArray {
        val hMm = if (s.paper == PaperType.RECEIPT) 40 else s.h
        return ascii(
            header(s, hMm) + "CLS\r\n" +
                "TEXT 24,24,\"3\",0,1,1,\"PrintBridge\"\r\n" +
                "BARCODE 24,104,\"128\",80,1,0,2,2,\"PRINTBRIDGE-OK\"\r\n" +
                "PRINT 1,1\r\n"
        )
    }

    fun calibrate(s: PrintSettings): ByteArray {
        val cmd = if (s.paper == PaperType.BLACK_MARK) "AUTODETECT" else "GAPDETECT"
        return ascii(header(s, s.h) + cmd + "\r\n")
    }

    /** Renders one PDF page to a 1-bit bitmap and wraps it in TSPL. */
    fun pdfPage(r: PdfRenderer, index: Int, s: PrintSettings): ByteArray {
        val dotsW = s.w * 8
        val dotsHMax = s.h * 8
        val receipt = s.paper == PaperType.RECEIPT

        val page = r.openPage(index)
        val src: Bitmap = try {
            val pw = page.width.toFloat()
            val ph = page.height.toFloat()
            val scale = if (receipt) dotsW / pw else minOf(dotsW / pw, dotsHMax / ph)
            val bw = (pw * scale).toInt().coerceAtLeast(1)
            val bh = (ph * scale).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            b.eraseColor(Color.WHITE)
            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            b
        } finally {
            page.close()
        }

        val w = dotsW
        val h = if (receipt) trimmedHeight(src) else dotsHMax
        val canvasBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(canvasBmp)
        cv.drawColor(Color.WHITE)
        cv.drawBitmap(src, (w - src.width) / 2f, 0f, null)

        val px = IntArray(w * h)
        canvasBmp.getPixels(px, 0, w, 0, 0, w, h)
        val g = IntArray(w * h) {
            val p = px[it]
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
        val black = BooleanArray(w * h)
        if (s.dither) {
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val old = g[i]
                val nw = if (old < 128) 0 else 255
                black[i] = nw == 0
                val err = old - nw
                if (x + 1 < w) g[i + 1] += err * 7 / 16
                if (y + 1 < h) {
                    if (x > 0) g[i + w - 1] += err * 3 / 16
                    g[i + w] += err * 5 / 16
                    if (x + 1 < w) g[i + w + 1] += err / 16
                }
            }
        } else {
            for (i in g.indices) black[i] = g[i] < s.threshold
        }

        val wb = w / 8
        val data = ByteArray(wb * h) { 0xFF.toByte() } // bit 0 = printed dot
        for (y in 0 until h) for (x in 0 until w) {
            val idx = if (s.rotate180) (h - 1 - y) * w + (w - 1 - x) else y * w + x
            val dot = black[idx] xor s.invertBits
            if (dot) {
                val k = y * wb + x / 8
                data[k] = (data[k].toInt() and (0x80 shr (x % 8)).inv()).toByte()
            }
        }

        val hMm = if (receipt) (h + 7) / 8 else s.h
        val head = ascii(header(s, hMm) + "CLS\r\nBITMAP ${s.offsetX},0,$wb,$h,0,")
        val tail = ascii("\r\nPRINT 1,1\r\n")
        return head + data + tail
    }

    private fun trimmedHeight(src: Bitmap): Int {
        val w = src.width
        val h = src.height
        val row = IntArray(w)
        var last = 0
        for (y in h - 1 downTo 0) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            if (row.any { Color.red(it) < 200 }) { last = y; break }
        }
        return maxOf(64, minOf(last + 24, h))
    }
}
