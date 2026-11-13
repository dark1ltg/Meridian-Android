package io.github.dark1ltg.meridian.playback

import android.app.Application
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import io.github.dark1ltg.meridian.data.TrackRow
import java.util.concurrent.atomic.AtomicReference

/**
 * UI-side handle to [PlaybackService]. Releasing this only drops the controller;
 * audio keeps running in the foreground service.
 */
@UnstableApi
class LocalPlayer(app: Application) {
    private val lock = Any()
    private val controllerRef = AtomicReference<MediaController?>(null)
    private val pending = mutableListOf<(Player) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val future = MediaController.Builder(
        app,
        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
    ).buildAsync()

    init {
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                val queued: List<(Player) -> Unit>
                synchronized(lock) {
                    controllerRef.set(controller)
                    queued = pending.toList()
                    pending.clear()
                }
                main.post { queued.forEach { it(controller) } }
            },
            MoreExecutors.directExecutor(),
        )
    }

    val player: Player?
        get() = controllerRef.get()

    fun whenReady(block: (Player) -> Unit) {
        val current = controllerRef.get()
        if (current != null) {
            main.post { block(current) }
            return
        }
        synchronized(lock) {
            val again = controllerRef.get()
            if (again != null) {
                main.post { block(again) }
            } else {
                pending.add(block)
            }
        }
    }

    fun play(track: TrackRow) {
        // DualDeck.play already prepare+plays (and starts the ~3s fade). A follow-up
        // prepare()/play() hits the incoming deck after session.setPlayer and can
        // look like a pause/resume at the start of the overlap.
        whenReady { p -> p.setMediaItem(MediaItems.fromTrack(track)) }
    }

    fun seek(ms: Long) {
        whenReady { it.seekTo(ms.coerceAtLeast(0L)) }
    }

    fun toggle() {
        whenReady { p ->
            if (p.isPlaying) p.pause() else p.play()
        }
    }

    fun skip() {
        // Prefer SessionViewModel.skip() (engine + setMediaItem). seekToNext is a
        // no-op on DualDeck's single-item timeline even when QueuePlayer overrides it.
        whenReady { it.seekToNext() }
    }

    fun previous() {
        whenReady { it.seekToPrevious() }
    }

    fun setVolume(value: Float) {
        whenReady { it.volume = value.coerceIn(0f, 1f) }
    }

    fun release() {
        synchronized(lock) { pending.clear() }
        MediaController.releaseFuture(future)
        controllerRef.set(null)
    }
}
