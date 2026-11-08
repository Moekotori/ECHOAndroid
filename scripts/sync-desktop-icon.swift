#!/usr/bin/env swift
// macOS: swift scripts/sync-desktop-icon.swift [path/to/selected/echo-app-icon.png]
// Historical filename; the default source is the selected Android launcher artwork.
import AppKit

let source = CommandLine.arguments.dropFirst().first ?? "scripts/assets/echo-app-icon.png"
guard let image = NSImage(contentsOfFile: source) else {
    fatalError("Cannot load the selected Android echo-app-icon.png")
}
let resources = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
    .appendingPathComponent("app/src/main/res")
func render(_ size: Int, adaptive: Bool, path: String) throws {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size,
        bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
        colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    let bounds = NSRect(x: 0, y: 0, width: size, height: size)
    NSGraphicsContext.current?.imageInterpolation = .high
    if adaptive {
        // Keep the full artwork in the middle 72dp of Android's 108dp layer,
        // extending only background edge pixels through the motion inset.
        let edge = CGFloat(size) * 2 / 3
        let inset = (CGFloat(size) - edge) / 2
        let starts: [CGFloat] = [0, inset, inset + edge]
        let lengths: [CGFloat] = [inset, edge, inset]
        let sourceX: [CGFloat] = [0, 0, image.size.width - 1]
        let sourceY: [CGFloat] = [0, 0, image.size.height - 1]
        let widths: [CGFloat] = [1, image.size.width, 1]
        let heights: [CGFloat] = [1, image.size.height, 1]
        for row in 0..<3 {
            for column in 0..<3 {
                image.draw(in: NSRect(x: starts[column], y: starts[row],
                    width: lengths[column], height: lengths[row]),
                    from: NSRect(x: sourceX[column], y: sourceY[row],
                        width: widths[column], height: heights[row]),
                    operation: .sourceOver, fraction: 1)
            }
        }
    } else {
        image.draw(in: bounds, from: .zero, operation: .sourceOver, fraction: 1)
    }
    NSGraphicsContext.restoreGraphicsState()
    try bitmap.representation(using: .png, properties: [:])!.write(to: resources.appendingPathComponent(path))
}
for (density, scale) in [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)] {
    try render(Int(48 * scale), adaptive: false, path: "mipmap-\(density)/ic_launcher.png")
    try render(Int(108 * scale), adaptive: true, path: "mipmap-\(density)/ic_launcher_foreground.png")
}
try Data(contentsOf: URL(fileURLWithPath: source))
    .write(to: URL(fileURLWithPath: "scripts/assets/echo-app-icon.png"))
