# PrintBridge

A thermal printer bridge application supporting **TSPL** and **ESC/POS** protocols over **USB OTG / WebUSB**.

## Features

- **USB Printer Discovery & Connection**: Supports real thermal printers (e.g. TSC TE210 VID `0x1203`, USB class 7) via WebUSB and OTG, alongside a built-in interactive printer emulator.
- **TSPL Command Builder**: Full TSPL syntax builder for `SIZE`, `GAP`, `BLINE`, `DIRECTION`, `REFERENCE`, `DENSITY`, `SPEED`, `CLS`, `TEXT`, `BARCODE` (Code 128), `QRCODE`, `BITMAP` (1bpp monochrome), `PRINT`, `SET CUTTER`, and `SET PEEL`.
- **ESC/POS Command Builder**: ESC/POS command stream generation with alignment, bold, line feeds, barcodes, QR codes, banded raster bitmaps, and paper cutting.
- **1bpp Raster & Dithering Engine**: Converts images/text into 1-bit packed monochrome rasters with optional Floyd-Steinberg error diffusion dithering and inversion.
- **Label & Stream Visualizer**: Real-time canvas label preview and raw ASCII/Hex command stream inspector.

## Development

```bash
npm install
npm run dev
```

Built with React 19, TypeScript, Vite, and Tailwind CSS.
