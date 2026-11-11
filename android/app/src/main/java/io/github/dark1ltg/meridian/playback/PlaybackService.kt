package io.github.dark1ltg.meridian.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
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
    private var volumeBeforeDuck: Float? = null
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pauseTransport(releaseFocus = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        focus = PlaybackAudioFocus(
            this,
            ownMediaCount = { decks?.playingCount() ?: 0 },
            onHold = { pauseTransport(releaseFocus = false) },
            onResume = { decks?.resumeActive() },
            onDuck = { duckVolume() },
            onUnduck = { unduckVolume() },
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
        // DualDeck plays the ExoPlayers directly; keep the mediaPlayback FGS up
        // whenever a deck is audible so hopping to another app does not mute us.
        super.onUpdateNotification(session, startInForegroundRequired || decks?.isAudible() == true)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (decks?.isAudible() == true) return
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

    private fun playTrack(track: TrackRow) {
        if (focus?.request() == false) return
        decks?.play(MediaItems.fromTrack(track))
    }

    private fun duckVolume() {
        val dual = decks ?: return
        if (volumeBeforeDuck == null) volumeBeforeDuck = dual.masterVolume
        dual.masterVolume = ((volumeBeforeDuck ?: dual.masterVolume) * 0.2f).coerceAtLeast(0.08f)
    }

    private fun unduckVolume() {
        val restore = volumeBeforeDuck ?: return
        volumeBeforeDuck = null
        decks?.masterVolume = restore
    }

    private fun engineQuiet(method: String) {
        io.execute {
            runCatching { engine.call(method) }
        }
    }

    private fun skipPayload(): JSONObject {
        val payload = JSONObject().put("position_ms", decks?.active?.currentPosition ?: 0L)
        decks?.active?.currentMediaItem?.mediaId?.toIntOrNull()?.let { payload.put("from_id", it) }
        return payload
    }

    /** Pause / unplug: bump epoch so a late finished_current cannot restart audio. */
    private fun pauseTransport(releaseFocus: Boolean = false) {
        transportEpoch += 1
        advancing = false
        if (releaseFocus) focus?.abandon()
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
            focus?.request()
            decks?.play(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
            setMediaItem(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            setMediaItem(mediaItem)
            if (startPositionMs > 0) decks?.seek(startPositionMs)
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

        override fun pause() {
            pauseTransport(releaseFocus = true)
        }

        override fun play() {
            if (focus?.request() == false) return
            decks?.resumeActive()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) play() else pause()
        }

        override fun stop() {
            transportEpoch += 1
            advancing = false
            focus?.abandon()
            decks?.stopAll()
        }

        override fun seekTo(positionMs: Long) {
            decks?.seek(positionMs)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            decks?.seek(positionMs)
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
