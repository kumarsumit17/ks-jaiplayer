package com.ks.jaiplayer.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Build tool (run by `gradle/app-cds.gradle.kts` after the app image is made): adds an AppCDS
 * archive to the finished macOS app, so the JVM maps the app's classes in from one file instead
 * of loading and checking each at start. Measured: first frame about 1.0–1.4 s instead of 2.0–2.2 s.
 *
 * JDK 17 with Compose's trimmed runtime has no base CDS archive, so a static archive is made:
 * 1. the app runs once in training mode (`JAIPLAYER_CDS_TRAIN=1`: it opens, shows the player,
 *    and quits by itself) and records the classes it loads;
 * 2. the app's own launcher dumps them into `app.jsa` beside its jars (`-Xshare:dump`);
 * 3. the launcher's options point at it (`-Xshare:auto`: a mismatched archive is simply ignored);
 * 4. the bundle is re-sealed, as after the plugin cache.
 * Any failure leaves the app exactly as it was, without an archive.
 */
fun main(args: Array<String>) {
    val root = File(args.first())
    val app = root.listFiles()?.firstOrNull { it.name.endsWith(".app") } ?: return println("AppCDS skipped: no app in $root")
    runCatching { addArchive(app) }
        .onSuccess { println("AppCDS archive added: ${File(app, "Contents/app/app.jsa").length() / (1024 * 1024)} MB") }
        .onFailure { println("AppCDS skipped: ${it.message}") }
}

private fun addArchive(app: File) {
    val name = app.name.removeSuffix(".app")
    val cfg = File(app, "Contents/app/$name.cfg")
    val launcher = File(app, "Contents/MacOS/$name")
    val archive = File(app, "Contents/app/app.jsa")
    check(cfg.isFile && launcher.canExecute()) { "launcher or its configuration missing" }
    val original = cfg.readText().lines().filterNot { it.contains("SharedArchiveFile") || it.contains("-Xshare") }
        .joinToString("\n").trimEnd() + "\n"
    val work = createTempDir()
    val classList = File(work, "classes.lst")
    try {
        cfg.writeText(original + "java-options=-XX:DumpLoadedClassList=${classList.path}\n")
        launch(launcher, timeoutSeconds = 90, train = true)
        check(classList.isFile && classList.readLines().size > 1000) { "training recorded no class list" }

        cfg.writeText(original + "java-options=-Xshare:dump\njava-options=-XX:SharedClassListFile=${classList.path}\njava-options=-XX:SharedArchiveFile=\$APPDIR/app.jsa\n")
        launch(launcher, timeoutSeconds = 180, train = false)
        check(archive.isFile && archive.length() > 0) { "the archive was not written" }

        cfg.writeText(original + "java-options=-XX:SharedArchiveFile=\$APPDIR/app.jsa\njava-options=-Xshare:auto\n")
        run("codesign", "--force", "--sign", "-", app.path)
        run("codesign", "--verify", "--deep", "--strict", app.path)
    } catch (e: Throwable) {
        cfg.writeText(original)
        archive.delete()
        runCatching { run("codesign", "--force", "--sign", "-", app.path) }
        throw e
    } finally {
        work.deleteRecursively()
    }
}

private fun launch(launcher: File, timeoutSeconds: Long, train: Boolean) {
    val pb = ProcessBuilder(launcher.path).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
    if (train) pb.environment()["JAIPLAYER_CDS_TRAIN"] = "1"
    val p = pb.start()
    if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        p.destroy()
        p.waitFor(10, TimeUnit.SECONDS)
        if (p.isAlive) p.destroyForcibly()
    }
}

private fun run(vararg command: String) {
    val p = ProcessBuilder(*command).inheritIO().start()
    check(p.waitFor() == 0) { "${command.joinToString(" ")} failed" }
}

private fun createTempDir(): File = kotlin.io.path.createTempDirectory("appcds").toFile()
