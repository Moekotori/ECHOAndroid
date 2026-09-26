package app.echo.android.plugin

import java.io.File
import java.util.zip.ZipInputStream

object PluginLimits {
    const val MaxZipBytes = 1024 * 1024
    const val MaxEntries = 64
    const val MaxEntryBytes = 256 * 1024
    const val MaxTotalBytes = 1024 * 1024
}

internal sealed class PackageInstall {
    class Ready(val manifest: PluginManifest) : PackageInstall()
    class Rejected(val reason: PluginRejectReason) : PackageInstall()
}

internal fun installPluginPackage(root: File, bytes: ByteArray): PackageInstall {
    if (bytes.isEmpty()) return PackageInstall.Rejected(PluginRejectReason.Unreadable)
    if (bytes.size > PluginLimits.MaxZipBytes) return PackageInstall.Rejected(PluginRejectReason.TooLarge)
    root.mkdirs()
    val staging = File(root, ".staging-${System.nanoTime()}")
    if (!staging.mkdirs()) return PackageInstall.Rejected(PluginRejectReason.Io)
    try {
        unzip(staging, bytes)?.let { return PackageInstall.Rejected(it) }
        val manifestFile = File(staging, "echo-plugin.json")
        if (!manifestFile.isFile) return PackageInstall.Rejected(PluginRejectReason.MissingManifest)
        val manifest = parsePluginManifest(manifestFile.readText(Charsets.UTF_8))
            ?: return PackageInstall.Rejected(PluginRejectReason.InvalidManifest)
        val script = File(staging, manifest.entry)
        val stagingPath = staging.canonicalFile.toPath()
        val scriptPath = script.canonicalFile.toPath()
        if (!script.isFile || !scriptPath.startsWith(stagingPath)) {
            return PackageInstall.Rejected(PluginRejectReason.MissingEntry)
        }
        val target = File(root, manifest.id)
        val savedStorage = File(target, "storage.json").takeIf { it.isFile }?.readBytes()
        val backup = File(root, ".backup-${manifest.id}")
        if (backup.exists() && !backup.deleteRecursively()) return PackageInstall.Rejected(PluginRejectReason.Io)
        if (target.exists() && !target.renameTo(backup)) return PackageInstall.Rejected(PluginRejectReason.Io)
        if (!staging.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target)
            return PackageInstall.Rejected(PluginRejectReason.Io)
        }
        if (savedStorage != null) {
            File(target, "storage.json").writeBytes(savedStorage)
        }
        if (backup.exists()) backup.deleteRecursively()
        return PackageInstall.Ready(manifest)
    } catch (_: Exception) {
        return PackageInstall.Rejected(PluginRejectReason.Io)
    } finally {
        if (staging.exists()) staging.deleteRecursively()
    }
}

private fun unzip(staging: File, bytes: ByteArray): PluginRejectReason? {
    var entries = 0
    var total = 0
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val relative = PluginPaths.safeRelative(entry.name)
                ?: return PluginRejectReason.UnsafePath
            entries += 1
            if (entries > PluginLimits.MaxEntries) return PluginRejectReason.TooManyFiles
            val outputFile = File(staging, relative)
            val stagingPath = staging.canonicalFile.toPath()
            if (!outputFile.canonicalFile.toPath().startsWith(stagingPath)) return PluginRejectReason.UnsafePath
            outputFile.parentFile?.mkdirs()
            var size = 0
            outputFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    size += read
                    total += read
                    if (size > PluginLimits.MaxEntryBytes || total > PluginLimits.MaxTotalBytes) {
                        return PluginRejectReason.TooLarge
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }
    return null
}
