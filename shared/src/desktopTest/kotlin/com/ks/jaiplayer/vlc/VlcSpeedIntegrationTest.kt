package com.ks.jaiplayer.vlc

import com.ks.jaiplayer.desktop.mediaRefOf
import com.ks.jaiplayer.player.PlaybackStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Fast playback keeps sound and picture going: at every offered speed above normal, time moves
 * at that speed, no audio buffer is lost, and pictures keep arriving. Runs only with a real
 * libVLC and JAIPLAYER_TEST_MEDIA (a video of at least 40 seconds); prints what it measured.
 */
class VlcSpeedIntegrationTest {
    private val vlcDir = System.getenv("JAIPLAYER_TEST_VLC")?.let(::File) ?: File("/Applications/VLC.app/Contents/MacOS")
    private val media = System.getenv("JAIPLAYER_TEST_MEDIA")?.let(::File)

    @Test fun fastSpeedsKeepSoundAndPicture(): Unit = runBlocking {
        if (!vlcDir.isDirectory || media?.isFile != true) return@runBlocking println("Skipped: VLC or JAIPLAYER_TEST_MEDIA missing")
        val engine = VlcPlayerEngine.create(
            listOf(VlcLocation(File(vlcDir, "lib"), File(vlcDir, "plugins"))),
            listOf("--codec=avcodec,all", "--avcodec-hw=none", VlcPlayerEngine.STATS_ARG),
        )
        try {
            engine.open(mediaRefOf(media!!))
            withTimeout(10_000) { engine.state.first { it.status == PlaybackStatus.Playing && it.durationMs > 0 } }
            val problems = mutableListOf<String>()
            for (speed in listOf(1f, 2f, 2.5f, 3f, 4f)) {
                engine.seekTo(0)
                engine.setSpeed(speed)
                delay(1_500) // settle after the seek and the rate change
                val s0 = engine.stats()!!
                val t0 = engine.state.value.positionMs
                val wall0 = System.nanoTime()
                delay(5_000)
                val s1 = engine.stats()!!
                val t1 = engine.state.value.positionMs
                val wallMs = (System.nanoTime() - wall0) / 1_000_000.0
                val rate = (t1 - t0) / wallMs
                val shown = (s1.displayedPictures - s0.displayedPictures) / (wallMs / 1000)
                val lostPictures = s1.lostPictures - s0.lostPictures
                val lostAudio = s1.lostAudioBuffers - s0.lostAudioBuffers
                val playedAudio = s1.playedAudioBuffers - s0.playedAudioBuffers
                println(
                    "speed=$speed rate=${"%.2f".format(rate)} shownFps=${"%.0f".format(shown)} " +
                        "lostPictures=$lostPictures audioPlayed=$playedAudio audioLost=$lostAudio",
                )
                if (kotlin.math.abs(rate - speed) > speed * 0.2) problems += "time moves at ${"%.2f".format(rate)}× at $speed×"
                if (lostAudio != 0) problems += "$lostAudio audio buffers lost at $speed×"
                if (playedAudio <= 0) problems += "no sound at $speed×"
                if (shown < 20) problems += "only ${"%.0f".format(shown)} pictures a second at $speed×"
            }
            assertTrue(problems.isEmpty(), problems.joinToString("; "))
        } finally {
            engine.release()
        }
    }
}
