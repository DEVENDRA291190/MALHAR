package com.printbridge.app

import android.content.Context

class SettingsStore(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("printbridge", Context.MODE_PRIVATE)

    fun load(): PrintSettings {
        val d = PrintSettings()
        return PrintSettings(
            transport = runCatching { Transport.valueOf(sp.getString("transport", d.transport.name)!!) }.getOrDefault(d.transport),
            printerId = sp.getString("printerId", "") ?: "",
            printerName = sp.getString("printerName", "") ?: "",
            paper = runCatching { PaperType.valueOf(sp.getString("paper", d.paper.name)!!) }.getOrDefault(d.paper),
            widthMm = sp.getInt("widthMm", d.widthMm),
            heightMm = sp.getInt("heightMm", d.heightMm),
            gapMm = sp.getInt("gapMm", d.gapMm),
            markMm = sp.getInt("markMm", d.markMm),
            pageMm = sp.getInt("pageMm", d.pageMm),
            density = sp.getInt("density", d.density),
            speed = sp.getInt("speed", d.speed),
            threshold = sp.getInt("threshold", d.threshold),
            dither = sp.getBoolean("dither", d.dither),
            invertBits = sp.getBoolean("invertBits", d.invertBits),
            rotate180 = sp.getBoolean("rotate180", d.rotate180),
            offsetXDots = sp.getInt("offsetXDots", d.offsetXDots),
        )
    }

    fun save(s: PrintSettings) {
        sp.edit()
            .putString("transport", s.transport.name)
            .putString("printerId", s.printerId)
            .putString("printerName", s.printerName)
            .putString("paper", s.paper.name)
            .putInt("widthMm", s.widthMm)
            .putInt("heightMm", s.heightMm)
            .putInt("gapMm", s.gapMm)
            .putInt("markMm", s.markMm)
            .putInt("pageMm", s.pageMm)
            .putInt("density", s.density)
            .putInt("speed", s.speed)
            .putInt("threshold", s.threshold)
            .putBoolean("dither", s.dither)
            .putBoolean("invertBits", s.invertBits)
            .putBoolean("rotate180", s.rotate180)
            .putInt("offsetXDots", s.offsetXDots)
            .apply()
    }
}
