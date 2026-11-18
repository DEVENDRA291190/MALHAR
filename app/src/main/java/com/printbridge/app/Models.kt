package com.printbridge.app

enum class Transport { USB, BLUETOOTH }
enum class PaperType { RECEIPT, GAP, BLACK_MARK }

data class PrinterEntry(val id: String, val name: String, val transport: Transport)

data class PrintSettings(
    val transport: Transport = Transport.USB,
    val printerId: String = "",
    val printerName: String = "",
    val paper: PaperType = PaperType.GAP,
    val widthMm: Int = 75,
    val heightMm: Int = 75,
    val gapMm: Int = 2,
    val markMm: Int = 2,
    val pageMm: Int = 160,
    val density: Int = 8,
    val speed: Int = 3,
    val threshold: Int = 128,
    val dither: Boolean = false,
    val invertBits: Boolean = false,
    val rotate180: Boolean = false,
    val offsetXDots: Int = 0,
) {
    val w: Int get() = widthMm.coerceIn(10, 104)
    val h: Int get() = heightMm.coerceIn(10, 400)
    val gap: Int get() = gapMm.coerceIn(0, 20)
    val mark: Int get() = markMm.coerceIn(0, 20)
    val page: Int get() = pageMm.coerceIn(10, 1000)
    val offsetX: Int get() = offsetXDots.coerceIn(0, 400)
}
