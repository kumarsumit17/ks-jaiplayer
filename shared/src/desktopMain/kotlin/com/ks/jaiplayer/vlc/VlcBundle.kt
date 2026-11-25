package com.ks.jaiplayer.vlc

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * The copy of libVLC that the desktop installers carry (specs/plan-bundled-vlc-and-plugins.md).
 * It holds libVLC and only the plugins that are LGPL, so the app stays MIT.
 */
object BundledVlc {
    /** The bundled libVLC inside the app's resources, or null when this build has none. */
    fun location(): VlcLocation? {
        val resources = System.getProperty("compose.application.resources.dir")?.let(::File) ?: return null
        val vlc = File(resources, "vlc")
        return when {
            File(vlc, "lib/libvlc.dylib").isFile -> VlcLocation(File(vlc, "lib"), File(vlc, "plugins"))
            // Windows: libVLC finds the "plugins" folder next to libvlc.dll by itself.
            File(vlc, "libvlc.dll").isFile -> VlcLocation(vlc)
            else -> null
        }
    }
}

/** Which of VLC's plugins may be bundled. */
object VlcPluginPolicy {
    /**
     * Plugins whose own code is LGPL but which, in VideoLAN's builds, link a library that is GPL
     * (or whose licence is not clearly LGPL-compatible). Left out.
     */
    val gplLibraries = setOf(
        "faad", "mad", "postproc", "dvdnav", "dvdread", "goom", "zvbi", "a52", "dca", "mpeg2",
        "cdda", "smb", "sid", "x262", "x264", "x26410b", "x265",
    )

    /** Interfaces the app never uses. */
    val unneeded = setOf("qt", "skins2")

    /** The plugin name in `libmp4_plugin.dylib` or `libmp4_plugin.dll`: `mp4`. */
    fun nameOf(fileName: String): String? = Regex("^lib(.+)_plugin\\.(dylib|dll|so)$").find(fileName)?.groupValues?.get(1)

    /** Null when [fileName] may be bundled; otherwise why not. [licence] is what the plugin declares, null if unclear. */
    fun exclusion(fileName: String, licence: String?): String? {
        val name = nameOf(fileName) ?: return "not a plugin"
        return when {
            licence == null -> "licence unclear"
            !licence.contains("Lesser General Public License") ->
                if (licence.contains("General Public License")) "GPL" else "licence unclear: $licence"
            name in gplLibraries || name.startsWith("arib") -> "links a GPL library"
            name in unneeded -> "not used by the app"
            else -> null
        }
    }
}

/**
 * Build tool, run by gradle/bundled-vlc.gradle.kts on the OS it packages for:
 * `<download url> <sha256> <download dir> <COPYING.LIB url> <its sha256> <vlc version> <out dir>`.
 * Downloads VLC (checked against the pinned SHA-256), keeps libVLC and the LGPL plugins, and
 * writes them with the licence and a plugin list to `<out dir>/common/vlc`.
 */
fun main(args: Array<String>) {
    require(args.size == 7) { "usage: <url> <sha256> <download dir> <licence url> <licence sha256> <version> <out dir>" }
    val (url, sha, downloadDir, licenceUrl, licenceSha) = args
    val version = args[5]
    val out = File(args[6], "common/vlc")
    val pkg = download(url, sha, File(downloadDir))
    val licence = download(licenceUrl, licenceSha, File(downloadDir))
    out.deleteRecursively()
    out.mkdirs()
    val report = if (pkg.name.endsWith(".dmg")) fromDmg(pkg, out) else fromZip(pkg, out)
    licence.copyTo(File(out, "COPYING.LIB"), overwrite = true)
    File(out, "PLUGINS.txt").writeText(report.text(version))
    println("Bundled libVLC $version: ${report.kept.size} plugins kept, ${report.excluded.size} left out")
}

private fun download(url: String, sha: String, dir: File): File {
    dir.mkdirs()
    val file = File(dir, url.substringAfterLast('/'))
    if (file.isFile && sha256(file) == sha) return file
    val partial = File(dir, file.name + ".part")
    URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
    val actual = sha256(partial)
    check(actual == sha) { "SHA-256 of $url is $actual, expected $sha" }
    partial.renameTo(file) || error("Could not move $partial")
    return file
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private class Report(val kept: List<Pair<String, String>>, val excluded: List<Pair<String, String>>) {
    fun text(version: String) = buildString {
        appendLine("libVLC $version and its plugins, by VideoLAN and the VLC authors.")
        appendLine("libVLC and every plugin below are licensed under the GNU Lesser General Public License,")
        appendLine("version 2.1 or later (COPYING.LIB). They are separate libraries, loaded at run time, and can be replaced.")
        appendLine("Source: https://download.videolan.org/pub/videolan/vlc/$version/vlc-$version.tar.xz")
        appendLine("Sources of the libraries built into the plugins: https://download.videolan.org/pub/videolan/contrib/")
        appendLine()
        appendLine("Included (${kept.size}):")
        kept.forEach { (file, licence) -> appendLine("  $file: $licence") }
        appendLine()
        appendLine("Left out, so that only LGPL code is shipped (${excluded.size}). An installed VLC is used when present:")
        excluded.forEach { (file, reason) -> appendLine("  $file: $reason") }
    }
}

/**
 * The licence a plugin declares (what its `vlc_entry_license` returns), read from the file: the
 * sentence is a constant in the binary. Loading the plugins instead would run their native
 * initialisers inside the build's JVM. Null when it holds neither sentence or both.
 */
internal fun declaredLicence(bytes: ByteArray): String? {
    val lgpl = contains(bytes, LGPL_SENTENCE)
    val gpl = contains(bytes, GPL_SENTENCE)
    return when {
        lgpl && !gpl -> LGPL_SENTENCE
        gpl && !lgpl -> GPL_SENTENCE
        else -> null
    }
}

internal const val LGPL_SENTENCE = "Licensed under the terms of the GNU Lesser General Public License, version 2.1 or later."
internal const val GPL_SENTENCE = "Licensed under the terms of the GNU General Public License, version 2 or later."

private fun contains(bytes: ByteArray, text: String): Boolean {
    val needle = text.toByteArray(Charsets.US_ASCII)
    outer@ for (i in 0..bytes.size - needle.size) {
        for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
        return true
    }
    return false
}

private fun selectPlugins(pluginRoot: File, out: File): Report {
    val kept = mutableListOf<Pair<String, String>>()
    val excluded = mutableListOf<Pair<String, String>>()
    pluginRoot.walkTopDown().filter { it.isFile && VlcPluginPolicy.nameOf(it.name) != null }.sortedBy { it.path }.forEach { plugin ->
        val licence = declaredLicence(plugin.readBytes())
        val relative = plugin.relativeTo(pluginRoot).invariantSeparatorsPath
        when (val reason = VlcPluginPolicy.exclusion(plugin.name, licence)) {
            null -> { plugin.copyTo(File(out, "plugins/$relative"), overwrite = true); kept += relative to licence!! }
            else -> excluded += relative to reason
        }
    }
    return Report(kept, excluded)
}

private fun fromDmg(dmg: File, out: File): Report {
    val mount = File.createTempFile("vlc-mount", "").apply { delete(); mkdirs() }
    run("hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen", "-mountpoint", mount.path, dmg.path)
    try {
        val macos = File(mount, "VLC.app/Contents/MacOS")
        // Real files under the names libVLC's install names use (@rpath/libvlc.dylib, @rpath/libvlccore.dylib).
        val lib = File(out, "lib").apply { mkdirs() }
        File(macos, "lib/libvlccore.dylib").canonicalFile.copyTo(File(lib, "libvlccore.dylib"))
        File(macos, "lib/libvlc.dylib").canonicalFile.copyTo(File(lib, "libvlc.dylib"))
        return selectPlugins(File(macos, "plugins"), out)
    } finally {
        run("hdiutil", "detach", "-force", mount.path)
    }
}

private fun fromZip(zip: File, out: File): Report {
    val unpacked = File.createTempFile("vlc-zip", "").apply { delete(); mkdirs() }
    ZipInputStream(zip.inputStream().buffered()).use { input ->
        while (true) {
            val entry = input.nextEntry ?: break
            val target = File(unpacked, entry.name).canonicalFile
            check(target.path.startsWith(unpacked.canonicalPath + File.separator)) { "Bad zip entry ${entry.name}" }
            if (entry.isDirectory) target.mkdirs() else { target.parentFile.mkdirs(); target.outputStream().use { input.copyTo(it) } }
        }
    }
    val root = unpacked.walkTopDown().first { File(it, "libvlc.dll").isFile }
    File(root, "libvlccore.dll").copyTo(File(out, "libvlccore.dll"))
    File(root, "libvlc.dll").copyTo(File(out, "libvlc.dll"))
    return selectPlugins(File(root, "plugins"), out)
}

private fun run(vararg command: String) {
    val process = ProcessBuilder(*command).inheritIO().start()
    check(process.waitFor() == 0) { "${command.joinToString(" ")} failed" }
}
