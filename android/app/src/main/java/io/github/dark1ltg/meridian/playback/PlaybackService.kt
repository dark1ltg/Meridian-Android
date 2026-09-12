package io.github.dark1ltg.meridian.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Environment
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
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                decks?.pauseBoth()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        focus = PlaybackAudioFocus(
            this,
            onHold = { decks?.pauseBoth() },
            onResume = { decks?.resumeActive() },
        )
        val a = newExo()
        val b = newExo()
        val dual = DualDeck(
            a = a,
            b = b,
            main = main,
            onActiveChanged = { exo -> session?.setPlayer(QueuePlayer(exo)) },
            onNearEnd = { advance("finished_current") },
            onEnded = { advance("finished_current") },
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
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).also {
                it.setSmallIcon(R.drawable.ic_stat_meridian)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (decks?.isAudible() != true) {
            stopSelf()
        }
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
        io.execute {
            val data = File(filesDir, "meridian").absolutePath
            val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
            runCatching {
                engine.call(
                    "initialize",
                    JSONObject().put("data_dir", data).put("default_music", music),
                )
            }
        }
    }

    private fun playTrack(track: TrackRow) {
        focus?.request()
        decks?.play(MediaItems.fromTrack(track))
    }

    private fun engineQuiet(method: String) {
        io.execute {
            runCatching { engine.call(method) }
        }
    }

    private fun skipPayload(): JSONObject {
        return JSONObject().put("position_ms", decks?.active?.currentPosition ?: 0L)
    }

    private fun advance(method: String, payload: JSONObject = JSONObject()) {
        if (method == "finished_current" && advancing) return
        if (method == "finished_current") advancing = true
        io.execute {
            try {
                val snap = engine.call(method, payload)
                val next = snap.current
                main.post {
                    if (method == "finished_current") advancing = false
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
            decks?.pauseActive()
        }

        override fun play() {
            focus?.request()
            decks?.resumeActive()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) play() else pause()
        }

        override fun stop() {
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
