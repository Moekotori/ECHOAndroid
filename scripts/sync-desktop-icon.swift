#!/usr/bin/env swift
// macOS: swift scripts/sync-desktop-icon.swift [path/to/desktop/echo-app-icon.png]
// Preserve desktop artwork; rasterize only Android density and safe-zone variants.
import AppKit

let source = CommandLine.arguments.dropFirst().first ?? "scripts/assets/echo-app-icon.png"
guard let image = NSImage(contentsOfFile: source) else {
    fatalError("Cannot load the desktop echo-app-icon.png")
}
let resources = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
    .appendingPathComponent("app/src/main/res")
func render(_ size: Int, fraction: CGFloat, background: Bool, monochrome: Bool = false, path: String) throws {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
        bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
        colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    let bounds = NSRect(x: 0, y: 0, width: size, height: size)
    if background {
        NSColor(srgbRed: 16/255, green: 29/255, blue: 53/255, alpha: 1).setFill()
        NSBezierPath(roundedRect: bounds, xRadius: CGFloat(size) * 0.23, yRadius: CGFloat(size) * 0.23).fill()
    }
    let edge = CGFloat(size) * fraction
    image.draw(in: NSRect(x: (CGFloat(size)-edge)/2, y: (CGFloat(size)-edge)/2, width: edge, height: edge),
        from: .zero, operation: .sourceOver, fraction: 1)
    if monochrome {
        NSColor.white.setFill()
        bounds.fill(using: .sourceIn)
    }
    NSGraphicsContext.restoreGraphicsState()
    try bitmap.representation(using: .png, properties: [:])!.write(to: resources.appendingPathComponent(path))
}
for (density, scale) in [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)] {
    try render(Int(48 * scale), fraction: 0.82, background: true, path: "mipmap-\(density)/ic_launcher.png")
    try render(Int(108 * scale), fraction: 0.50, background: false, path: "mipmap-\(density)/ic_launcher_foreground.png")
}
try render(432, fraction: 0.50, background: false, monochrome: true, path: "drawable-nodpi/echo_brand_monochrome.png")
try Data(contentsOf: URL(fileURLWithPath: source))
    .write(to: URL(fileURLWithPath: "scripts/assets/echo-app-icon.png"))
