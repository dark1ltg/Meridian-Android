package io.github.dark1ltg.meridian.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.dark1ltg.meridian.R
import io.github.dark1ltg.meridian.analysis.FfmpegPcmDecoder
import io.github.dark1ltg.meridian.data.Engine
import io.github.dark1ltg.meridian.data.TrackRow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Foreground media session so playback survives the lockscreen, notification shade,
 * and hopping to other apps. Two ExoPlayer decks crossfade like the desktop player.
 * Skip / previous / completion go through the Python queue.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private val engine = Engine()
    private val io = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var session: MediaSession? = null
    private var decks: DualDeck? = null
    private var focus: PlaybackAudioFocus? = null
    private var advancing = false
    /** Bumped on UI setMediaItem/play so a late finished_current cannot overwrite the tap. */
    private var transportEpoch = 0
    private var pendingAfterHold: MediaItem? = null
    private var pendingStartMs: Long = 0L
    private var fgsRetries = 0
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            // Unplug during a call must not drop pendingAfterHold / abandon focus.
            pauseTransport(releaseFocus = focus?.isHoldingForCall() != true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        focus = PlaybackAudioFocus(
            this,
            onHold = { pauseTransport(releaseFocus = false) },
            onResume = { resumeAfterHold() },
            onDuck = { decks?.duckMultiplier = 0.2f },
            onUnduck = { decks?.duckMultiplier = 1f },
        )
        val a = newExo()
        val b = newExo()
        val dual = DualDeck(
            a = a,
            b = b,
            main = main,
            onActiveChanged = { exo -> session?.setPlayer(QueuePlayer(exo)) },
            onNearEnd = { advanceFinished() },
            onEnded = { advanceFinished() },
            onError = { id ->
                val payload = JSONObject()
                id?.toIntOrNull()?.let { payload.put("id", it) }
                advance("playback_failed", payload)
            },
            onFadeSettled = { engineQuiet("fade_settled") },
        )
        decks = dual
        bootEngine()
        ContextCompat.registerReceiver(
            this,
            noisy,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val launch = DeviceUnlock.sessionActivityPendingIntent(this)
        session = MediaSession.Builder(this, QueuePlayer(dual.active))
            .setSessionActivity(launch)
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult {
                        // Single-item DualDeck timelines drop SEEK_TO_NEXT/PREV from ExoPlayer's
                        // native commands; keep them so notification / MediaController skip works.
                        val playerCmds = Player.Commands.Builder()
                            .addAll(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                            .add(Player.COMMAND_SEEK_TO_NEXT)
                            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                            .build()
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailablePlayerCommands(playerCmds)
                            .build()
                    }
                },
            )
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).also {
                it.setSmallIcon(R.drawable.ic_stat_meridian)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val keep = startInForegroundRequired ||
            decks?.isAudible() == true ||
            decks?.hasMedia() == true ||
            holdingForResume()
        try {
            super.onUpdateNotification(session, keep)
            fgsRetries = 0
        } catch (e: RuntimeException) {
            // API 31+ ForegroundServiceStartNotAllowedException — keep playing without
            // crashing, then retry a couple of times in case the restriction lifts.
            if (Build.VERSION.SDK_INT >= 31 &&
                e.javaClass.simpleName == "ForegroundServiceStartNotAllowedException"
            ) {
                runCatching { super.onUpdateNotification(session, false) }
                if (keep && fgsRetries < 2) {
                    fgsRetries += 1
                    main.postDelayed({
                        val live = this.session ?: return@postDelayed
                        onUpdateNotification(live, true)
                    }, 400L)
                }
            } else {
                throw e
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (decks?.isAudible() == true ||
            holdingForResume() ||
            decks?.hasMedia() == true
        ) {
            return
        }
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisy) }
        focus?.abandon()
        session?.release()
        session = null
        decks?.release()
        decks = null
        io.shutdownNow()
        super.onDestroy()
    }

    private fun newExo(): ExoPlayer {
        return ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setRenderersFactory(
                DefaultRenderersFactory(this).setExtensionRendererMode(
                    if (FfmpegPcmDecoder.isAvailable()) {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    } else {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    },
                ),
            )
            .build()
    }

    private fun bootEngine() {
        // Same Chaquopy AndroidSession as the UI. Never call initialize() here —
        // that closes the live library and made Play look broken after Scan/Listen.
        io.execute {
            runCatching {
                (application as? io.github.dark1ltg.meridian.MeridianApp)?.ensurePython()
            }
            val data = File(filesDir, "meridian").absolutePath
            runCatching {
                engine.call("ensure", JSONObject().put("data_dir", data))
            }
        }
    }

    private fun holdingForResume(): Boolean {
        return focus?.isHoldingForCall() == true || pendingAfterHold != null
    }

    private fun stashPending(item: MediaItem, startMs: Long = 0L) {
        pendingAfterHold = item
        pendingStartMs = startMs.coerceAtLeast(0L)
    }

    private fun playTrack(track: TrackRow) {
        playMedia(MediaItems.fromTrack(track))
    }

    private fun playMedia(item: MediaItem, startMs: Long = 0L) {
        // Do not re-request while a call/alarm has us: a grant would clear
        // resumeOnGain and start audio over the call.
        if (focus?.isHoldingForCall() == true) {
            stashPending(item, startMs)
            return
        }
        val ok = focus?.request() ?: true
        if (focus?.isHoldingForCall() == true || focus?.foreignAudioShouldBlock() == true) {
            stashPending(item, startMs)
            return
        }
        if (!ok) return
        pendingAfterHold = null
        pendingStartMs = 0L
        decks?.play(item)
        if (startMs > 0L) decks?.seek(startMs)
    }

    private fun resumeAfterHold() {
        val pending = pendingAfterHold
        val start = pendingStartMs
        pendingAfterHold = null
        pendingStartMs = 0L
        if (pending != null) {
            playMedia(pending, start)
        } else {
            decks?.resumeActive()
        }
    }

    private fun engineQuiet(method: String) {
        io.execute {
            runCatching { engine.call(method) }
        }
    }

    private fun skipPayload(): JSONObject {
        val payload = JSONObject().put("position_ms", decks?.skipPositionMs() ?: 0L)
        decks?.skipFromId()?.toIntOrNull()?.let { payload.put("from_id", it) }
        return payload
    }

    /** Pause / unplug. User pause bumps epoch; focus-hold storms must not. */
    private fun pauseTransport(releaseFocus: Boolean = false) {
        if (releaseFocus) {
            pendingAfterHold = null
            pendingStartMs = 0L
            transportEpoch += 1
            advancing = false
            focus?.abandon()
            decks?.pauseBoth()
            return
        }
        if (decks?.isTransportPaused() == true) return
        advancing = false
        decks?.pauseBoth()
    }

    private fun advanceFinished() {
        val endedId = decks?.active?.currentMediaItem?.mediaId?.toIntOrNull() ?: 0
        val payload = JSONObject()
        if (endedId > 0) payload.put("ended_id", endedId)
        advance("finished_current", payload)
    }

    private fun advance(method: String, payload: JSONObject = JSONObject()) {
        if (method == "finished_current" && advancing) return
        if (method == "finished_current") advancing = true
        // Skip/previous/error must invalidate an in-flight finished_current autoplay.
        if (method != "finished_current") transportEpoch += 1
        val epochAtStart = transportEpoch
        io.execute {
            try {
                val snap = engine.call(method, payload)
                val next = snap.current
                main.post {
                    if (method == "finished_current") advancing = false
                    // A newer tap/skip/pause owns the decks — drop this stale engine result.
                    if (epochAtStart != transportEpoch) return@post
                    if (next != null && next.path.isNotBlank()) {
                        playTrack(next)
                    } else {
                        decks?.stopAll()
                    }
                }
            } catch (_: Throwable) {
                main.post { if (method == "finished_current") advancing = false }
            }
        }
    }

    private inner class QueuePlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(COMMAND_SEEK_TO_NEXT)
                .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(COMMAND_SEEK_TO_PREVIOUS)
                .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }

        override fun isCommandAvailable(command: @Player.Command Int): Boolean {
            return when (command) {
                COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun getMediaMetadata(): MediaMetadata {
            return super.getMediaMetadata().withoutArtwork()
        }

        override fun getPlaylistMetadata(): MediaMetadata {
            return super.getPlaylistMetadata().withoutArtwork()
        }

        override fun hasNextMediaItem(): Boolean = true

        override fun hasPreviousMediaItem(): Boolean = true

        override fun prepare() {
            decks?.active?.prepare()
        }

        override fun setMediaItem(mediaItem: MediaItem) {
            transportEpoch += 1
            advancing = false
            playMedia(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
            setMediaItem(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            transportEpoch += 1
            advancing = false
            playMedia(mediaItem, startPositionMs)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
            mediaItems.firstOrNull()?.let { setMediaItem(it) }
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            setMediaItems(mediaItems)
        }

        override fun setMediaItems(
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ) {
            val item = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull() ?: return
            setMediaItem(item, startPositionMs)
        }

        override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
            setMediaItem(mediaItem)
        }

        override fun replaceMediaItems(
            fromIndex: Int,
            toIndex: Int,
            mediaItems: MutableList<MediaItem>,
        ) {
            mediaItems.firstOrNull()?.let { setMediaItem(it) }
        }

        override fun addMediaItem(mediaItem: MediaItem) {
            setMediaItem(mediaItem)
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            setMediaItem(mediaItem)
        }

        override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
            setMediaItems(mediaItems)
        }

        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
            setMediaItems(mediaItems)
        }

        override fun isPlaying(): Boolean {
            val dual = decks ?: return super.isPlaying()
            return !dual.isTransportPaused() && dual.isAudible()
        }

        override fun getPlayWhenReady(): Boolean {
            val dual = decks ?: return super.getPlayWhenReady()
            return !dual.isTransportPaused() && dual.hasMedia()
        }

        override fun pause() {
            if (focus?.isHoldingForCall() == true) {
                focus?.markPausedUntilUser()
                pauseTransport(releaseFocus = false)
                return
            }
            pauseTransport(releaseFocus = true)
        }

        override fun play() {
            if (focus?.isHoldingForCall() == true) return
            val ok = focus?.request() ?: true
            if (focus?.isHoldingForCall() == true || focus?.foreignAudioShouldBlock() == true) return
            if (!ok) return
            val pending = pendingAfterHold
            val start = pendingStartMs
            pendingAfterHold = null
            pendingStartMs = 0L
            if (pending != null) {
                playMedia(pending, start)
            } else {
                decks?.resumeActive()
            }
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) play() else pause()
        }

        override fun stop() {
            transportEpoch += 1
            advancing = false
            pendingAfterHold = null
            pendingStartMs = 0L
            focus?.abandon()
            decks?.stopAll()
        }

        override fun seekTo(positionMs: Long) {
            if (focus?.isHoldingForCall() == true && pendingAfterHold != null) {
                pendingStartMs = positionMs.coerceAtLeast(0L)
                return
            }
            decks?.seek(positionMs)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            seekTo(positionMs)
        }

        override fun setVolume(volume: Float) {
            val dual = decks ?: return
            dual.masterVolume = volume
        }

        override fun getVolume(): Float = decks?.masterVolume ?: super.getVolume()

        override fun seekToNext() {
            advance("skip", skipPayload())
        }

        override fun seekToNextMediaItem() {
            advance("skip", skipPayload())
        }

        override fun seekToPrevious() {
            if (decks?.crossfading == true) {
                decks?.stopAll(notifySettle = false)
                advance("previous")
            } else if (currentPosition > 4_000L) {
                seekTo(0)
            } else {
                advance("previous")
            }
        }

        override fun seekToPreviousMediaItem() {
            seekToPrevious()
        }
    }
}
