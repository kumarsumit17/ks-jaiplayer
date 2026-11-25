package com.ks.jaiplayer.vlc

import com.ks.jaiplayer.desktop.collectMedia
import com.ks.jaiplayer.desktop.fileUri
import com.ks.jaiplayer.desktop.mediaRefOf
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.PlayerError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plays a real file with the installed libVLC. Runs only where VLC.app is installed and
 * JAIPLAYER_TEST_MEDIA names a video of at least 20 s with two audio tracks; skipped otherwise
 * (CI has no VLC). JAIPLAYER_TEST_VLC=<folder with lib/ and plugins/> tests that libVLC instead,
 * for example the bundled copy in desktop/mac/build/vlc-resources/common/vlc.
 */
class VlcEngineIntegrationTest {
    private val vlcApp = File("/Applications/VLC.app")
    private val vlcDir = System.getenv("JAIPLAYER_TEST_VLC")?.let(::File) ?: File(vlcApp, "Contents/MacOS")
    private val media = System.getenv("JAIPLAYER_TEST_MEDIA")?.let(::File)

    private fun engineOrNull(): VlcPlayerEngine? {
        if (!vlcDir.isDirectory || media?.isFile != true) return null
        return VlcPlayerEngine.create(
            listOf(VlcLocation(File(vlcDir, "lib"), File(vlcDir, "plugins"))),
            // The same options as desktop/mac's entry point (MAC_VLC_ARGS).
            listOf("--codec=avcodec,all", "--avcodec-hw=none"),
        )
    }

    private suspend fun VlcPlayerEngine.await(what: String, ms: Long = 10_000, test: (PlaybackState) -> Boolean) =
        runCatching { withTimeout(ms) { state.first(test) } }.getOrElse { error("Timed out waiting for $what; state: ${state.value}") }

    @Test fun playsSeeksChangesSpeedAndTracks(): Unit = runBlocking {
        val engine = engineOrNull() ?: return@runBlocking println("Skipped: VLC or JAIPLAYER_TEST_MEDIA missing")
        try {
            assertTrue(engine.vlcVersion!!.startsWith("3."))
            engine.open(mediaRefOf(media!!))
            engine.await("playing") { it.status == PlaybackStatus.Playing && it.durationMs > 0 && it.hasVideo }
            val s = engine.await("two audio tracks") { it.audioTracks.size == 2 }
            assertTrue(s.durationMs >= 20_000, "duration ${s.durationMs}")
            assertTrue(engine.frames.width > 0 && engine.frames.height > 0)

            engine.seekTo(15_000)
            engine.await("seek") { it.positionMs in 14_000..18_000 }

            engine.setSpeed(2f)
            assertEquals(2f, engine.state.value.speed)

            val second = s.audioTracks[1].id
            engine.selectAudioTrack(second)
            engine.await("audio track") { it.audioTrackId == second }

            val srt = File.createTempFile("jaiplayer", ".srt").apply {
                writeText("1\n00:00:00,000 --> 00:10:00,000\nHello from KS JaiPlayer subtitles\n")
                deleteOnExit()
            }
            val before = brightPixelsInBottomQuarter(engine)
            engine.addSubtitle(fileUri(srt))
            engine.await("subtitle track") { it.subtitleTracks.isNotEmpty() }
            delay(1_500)
            val withSubtitle = brightPixelsInBottomQuarter(engine)
            // The subtitle is drawn into the frames libVLC hands us, so it shows in the app.
            assertTrue(withSubtitle > before + 100, "subtitle not drawn: $before → $withSubtitle bright pixels")

            engine.pause()
            engine.await("paused") { it.status == PlaybackStatus.Paused }
        } finally {
            engine.release()
        }
    }

    /** Near-white pixels in the bottom quarter of the latest frame (BGRA). */
    private fun brightPixelsInBottomQuarter(engine: VlcPlayerEngine): Int = synchronized(engine.frames.lock) {
        val w = engine.frames.width
        val h = engine.frames.height
        val px = engine.frames.pixels
        var n = 0
        for (y in h * 3 / 4 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            if ((px[i].toInt() and 0xFF) > 230 && (px[i + 1].toInt() and 0xFF) > 230 && (px[i + 2].toInt() and 0xFF) > 230) n++
        }
        n
    }

    @Test fun missingVlcIsReportedNotThrown() {
        val engine = VlcPlayerEngine.create(listOf(VlcLocation(File("/nonexistent"))))
        assertEquals(PlayerError.EngineMissing, engine.state.value.error)
        engine.open(mediaRefOf(File("/nonexistent.mp4"))) // no crash, nothing happens
        engine.release()
    }

    @Test fun foldersExpandInEpisodeOrder() {
        val dir = createTempDirTree()
        val names = collectMedia(listOf(dir)).map { it.relativeTo(dir).invariantSeparatorsPath }
        assertEquals(listOf("Season 1/E2.mkv", "Season 1/E10.mkv", "Season 2/E1.mp4"), names)
        dir.deleteRecursively()
    }

    private fun createTempDirTree(): File {
        val dir = kotlin.io.path.createTempDirectory("series").toFile()
        listOf("Season 2/E1.mp4", "Season 1/E10.mkv", "Season 1/E2.mkv", "Season 1/notes.txt", ".hidden/E0.mkv", "Season 1/._E3.mkv")
            .forEach { File(dir, it).apply { parentFile.mkdirs(); writeText("x") } }
        return dir
    }
}
