package com.ks.jaiplayer.vlc

import com.ks.jaiplayer.player.MediaRef
import com.ks.jaiplayer.player.clampSpeed
import com.ks.jaiplayer.player.PlaybackState
import com.ks.jaiplayer.player.PlaybackStatus
import com.ks.jaiplayer.player.PlayerEngine
import com.ks.jaiplayer.player.PlayerError
import com.ks.jaiplayer.player.Track
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * The desktop engine (macOS and Windows): libVLC 3.x, drawing through video callbacks.
 *
 * Every libVLC call runs on one thread of its own, in order, so the interface never waits for
 * libVLC and libVLC is never called from two threads at once. The state is polled five times a
 * second, which is enough for the time display and simpler than libVLC's event callbacks.
 */
class VlcPlayerEngine private constructor(
    private val vlc: LoadedVlc?,
    private val extraArgs: List<String>,
) : PlayerEngine {

    private val _state = MutableStateFlow(
        if (vlc == null) PlaybackState(error = PlayerError.EngineMissing) else PlaybackState(),
    )
    override val state: StateFlow<PlaybackState> = _state

    internal val frames = VideoFrames()

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "libvlc").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    private val lib = vlc?.lib
    private var instance: Pointer? = null
    private var player: Pointer? = null
    private var wantedVolume = 100
    private var wantedMute = false
    private var wantedSpeed = 1f
    private var everPlayed = false
    private val pendingSeek = AtomicLong(-1)
    @Volatile private var seekTarget = -1L
    @Volatile private var seekShownUntil = 0L

    /** The libVLC version in use, or null when it could not be loaded. */
    val vlcVersion: String? = vlc?.version

    init {
        if (lib != null) scope.launch {
            val args = vlcArgs(withStats = FrameStats.enabled || STATS_ARG in extraArgs) + extraArgs.filter { it != STATS_ARG }
            val inst = lib.libvlc_new(args.size, args)
            val mp = inst?.let(lib::libvlc_media_player_new)
            if (inst == null || mp == null) {
                _state.value = PlaybackState(error = PlayerError.EngineMissing)
                return@launch
            }
            lib.libvlc_video_set_format_callbacks(mp, frames.format, frames.cleanup)
            lib.libvlc_video_set_callbacks(mp, frames.lockFrame, null, frames.display, null)
            instance = inst
            player = mp
            poll()
        }
    }

    private inline fun call(crossinline block: LibVlc.(Pointer) -> Unit) {
        val l = lib ?: return
        scope.launch { player?.let { l.block(it) } }
    }

    override fun open(media: MediaRef) {
        val l = lib ?: return
        _state.update {
            it.copy(status = PlaybackStatus.Opening, media = media, positionMs = 0, durationMs = 0, error = it.error.takeIf { e -> e == PlayerError.EngineMissing },
                hasVideo = false, audioTracks = emptyList(), subtitleTracks = emptyList(),
                audioTrackId = null, subtitleTrackId = null)
        }
        scope.launch {
            val inst = instance ?: return@launch
            val mp = player ?: return@launch
            everPlayed = false
            l.libvlc_media_player_stop(mp)
            frames.clear()
            val path = runCatching { File(URI(media.uri)).path }.getOrNull()
            val m = path?.let { l.libvlc_media_new_path(inst, it) }
            if (m == null) {
                _state.update { it.copy(status = PlaybackStatus.Error, error = PlayerError.CannotOpen) }
                return@launch
            }
            l.libvlc_media_player_set_media(mp, m)
            l.libvlc_media_release(m)
            if (l.libvlc_media_player_play(mp) != 0) {
                _state.update { it.copy(status = PlaybackStatus.Error, error = PlayerError.CannotOpen) }
            }
        }
    }

    override fun play() = call { mp ->
        when (libvlc_media_player_get_state(mp)) {
            LibVlc.STATE_PAUSED -> libvlc_media_player_set_pause(mp, 0)
            LibVlc.STATE_ENDED, LibVlc.STATE_STOPPED -> {
                libvlc_media_player_stop(mp)
                libvlc_media_player_play(mp)
            }
            else -> libvlc_media_player_play(mp)
        }
    }

    override fun pause() = call { mp -> libvlc_media_player_set_pause(mp, 1) }

    override fun seekTo(positionMs: Long) {
        _state.update { it.copy(positionMs = positionMs) }
        seekShownUntil = System.nanoTime() + SEEK_SETTLE_NS
        seekTarget = positionMs
        // Seeks are coalesced: while one runs, newer requests replace each other, and only the
        // last one is sent. Dragging the bar or holding an arrow key never builds a queue.
        if (pendingSeek.getAndSet(positionMs) < 0) call { mp ->
            val target = pendingSeek.getAndSet(-1)
            if (target >= 0) libvlc_media_player_set_time(mp, target)
        }
    }

    override fun setVolume(volume: Int) {
        wantedVolume = volume.coerceIn(0, 100)
        _state.update { it.copy(volume = wantedVolume) }
        call { mp -> libvlc_audio_set_volume(mp, wantedVolume) }
    }

    override fun setMuted(muted: Boolean) {
        wantedMute = muted
        _state.update { it.copy(muted = muted) }
        call { mp -> libvlc_audio_set_mute(mp, if (muted) 1 else 0) }
    }

    override fun setSpeed(speed: Float) {
        wantedSpeed = clampSpeed(speed)
        _state.update { it.copy(speed = wantedSpeed) }
        call { mp -> libvlc_media_player_set_rate(mp, wantedSpeed) }
    }

    override fun selectAudioTrack(id: Int) {
        _state.update { it.copy(audioTrackId = id) }
        call { mp -> libvlc_audio_set_track(mp, id) }
    }

    override fun selectSubtitleTrack(id: Int?) {
        _state.update { it.copy(subtitleTrackId = id) }
        call { mp -> libvlc_video_set_spu(mp, id ?: -1) }
    }

    override fun addSubtitle(uri: String) = call { mp ->
        libvlc_media_player_add_slave(mp, LibVlc.SLAVE_SUBTITLE, uri, 1)
    }

    override fun stop() {
        _state.update { PlaybackState(volume = it.volume, muted = it.muted, speed = it.speed, error = it.error.takeIf { e -> e == PlayerError.EngineMissing }) }
        call { mp -> libvlc_media_player_stop(mp) }
        frames.clear()
    }

    override fun release() {
        val l = lib
        scope.launch {
            player?.let { l?.libvlc_media_player_stop(it); l?.libvlc_media_player_release(it) }
            instance?.let { l?.libvlc_release(it) }
            player = null
            instance = null
        }.invokeOnCompletion {
            scope.cancel()
            executor.shutdown()
        }
    }

    /** libVLC's counters for the current media (only with statistics on), read on the libVLC thread. */
    internal suspend fun stats(): VlcStats? = kotlinx.coroutines.withContext(scope.coroutineContext) {
        val l = lib ?: return@withContext null
        val media = player?.let(l::libvlc_media_player_get_media) ?: return@withContext null
        val mem = com.sun.jna.Memory(15L * 4)
        val ok = l.libvlc_media_get_stats(media, mem) != 0
        l.libvlc_media_release(media)
        if (!ok) null else VlcStats(
            decodedVideo = mem.getInt(6L * 4), decodedAudio = mem.getInt(7L * 4),
            displayedPictures = mem.getInt(8L * 4), lostPictures = mem.getInt(9L * 4),
            playedAudioBuffers = mem.getInt(10L * 4), lostAudioBuffers = mem.getInt(11L * 4),
        )
    }

    // ---- State polling -------------------------------------------------------------------------

    private suspend fun poll() {
        val l = lib ?: return
        var ticks = 0
        var lastVlcState = -1
        while (scope.isActive) {
            val mp = player ?: return
            val vlcState = l.libvlc_media_player_get_state(mp)
            val current = _state.value
            if (current.media != null && current.status != PlaybackStatus.Idle) {
                val status = when (vlcState) {
                    LibVlc.STATE_OPENING -> PlaybackStatus.Opening
                    LibVlc.STATE_BUFFERING -> if (everPlayed) current.status else PlaybackStatus.Opening
                    LibVlc.STATE_PLAYING -> PlaybackStatus.Playing
                    LibVlc.STATE_PAUSED -> PlaybackStatus.Paused
                    LibVlc.STATE_ENDED -> PlaybackStatus.Ended
                    LibVlc.STATE_ERROR -> PlaybackStatus.Error
                    else -> current.status
                }
                if (vlcState == LibVlc.STATE_PLAYING && lastVlcState != LibVlc.STATE_PLAYING) {
                    // The audio output exists only once playing; apply the wanted volume then.
                    l.libvlc_audio_set_volume(mp, wantedVolume)
                    l.libvlc_audio_set_mute(mp, if (wantedMute) 1 else 0)
                    // The speed carries over from one episode to the next.
                    l.libvlc_media_player_set_rate(mp, wantedSpeed)
                    everPlayed = true
                }
                val refreshTracks = vlcState != lastVlcState || ticks % 5 == 0
                val length = l.libvlc_media_player_get_length(mp).coerceAtLeast(0)
                val polled = if (status == PlaybackStatus.Ended) length else l.libvlc_media_player_get_time(mp).coerceAtLeast(0)
                // Just after a seek libVLC may still report the old time; keep showing the target
                // until it arrives (or a moment passes), so the bar never jumps back and forth.
                val settling = seekTarget >= 0 && System.nanoTime() < seekShownUntil &&
                    kotlin.math.abs(polled - seekTarget) > SEEK_NEAR_MS
                if (!settling) seekTarget = -1
                val time = if (settling) current.positionMs else polled
                _state.update {
                    var next = it.copy(
                        status = status,
                        positionMs = time,
                        durationMs = length,
                        hasVideo = frames.width > 0,
                        error = when {
                            status != PlaybackStatus.Error -> it.error.takeIf { e -> e == PlayerError.EngineMissing }
                            everPlayed -> PlayerError.Playback
                            else -> PlayerError.CannotOpen
                        },
                    )
                    if (refreshTracks && vlcState in PLAYING_STATES) {
                        val audio = tracks(l, l.libvlc_audio_get_track_description(mp))
                        val subs = tracks(l, l.libvlc_video_get_spu_description(mp))
                        next = next.copy(
                            audioTracks = audio,
                            subtitleTracks = subs,
                            audioTrackId = l.libvlc_audio_get_track(mp).takeIf { id -> id >= 0 },
                            subtitleTrackId = l.libvlc_video_get_spu(mp).takeIf { id -> id >= 0 },
                        )
                    }
                    next
                }
            }
            lastVlcState = vlcState
            ticks++
            delay(POLL_MS)
        }
    }

    /** Reads a `libvlc_track_description_t` list (id, name, next) and frees it; skips "Disable" (-1). */
    private fun tracks(l: LibVlc, list: Pointer?): List<Track> {
        list ?: return emptyList()
        val out = mutableListOf<Track>()
        var p: Pointer? = list
        val ptr = Native.POINTER_SIZE.toLong()
        while (p != null) {
            val id = p.getInt(0)
            val name = p.getPointer(ptr)?.getString(0, "UTF-8")
            if (id >= 0) out += Track(id, name)
            p = p.getPointer(ptr * 2)
        }
        l.libvlc_track_description_list_release(list)
        return out
    }

    companion object {
        private const val POLL_MS = 200L
        private const val SEEK_SETTLE_NS = 1_500_000_000L
        private const val SEEK_NEAR_MS = 1_000L
        private val PLAYING_STATES = setOf(LibVlc.STATE_PLAYING, LibVlc.STATE_PAUSED)
        private val VLC_ARGS = arrayOf("--no-video-title-show", "--no-snapshot-preview")

        /** Passed in extra arguments (tests) to collect libVLC's playback statistics; see [stats]. */
        internal const val STATS_ARG = "--stats"

        /** For diagnosing playback problems: JAIPLAYER_VLC_LOG=<file> writes libVLC's detailed log there. */
        private fun vlcArgs(withStats: Boolean): Array<String> {
            val base = if (withStats) VLC_ARGS else VLC_ARGS + "--no-stats"
            val log = System.getenv("JAIPLAYER_VLC_LOG")?.takeIf { it.isNotBlank() }
            return if (log == null) base + "--quiet" else base + arrayOf("--file-logging", "--logfile=$log", "--log-verbose=2")
        }

        /** Loads libVLC from the first working [locations]; without one, the engine reports [PlayerError.EngineMissing]. */
        fun create(locations: List<VlcLocation>, extraArgs: List<String> = emptyList()): VlcPlayerEngine =
            VlcPlayerEngine(VlcLoader.load(locations), extraArgs)
    }
}

/** A few of libVLC's playback counters (libvlc_media_stats_t), totals since the media opened. */
internal data class VlcStats(
    val decodedVideo: Int,
    val decodedAudio: Int,
    val displayedPictures: Int,
    val lostPictures: Int,
    val playedAudioBuffers: Int,
    val lostAudioBuffers: Int,
)
