package io.github.dark1ltg.meridian.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * One focus owner for both crossfade decks. ExoPlayer audio-focus handling is
 * off so the incoming deck cannot pause the outgoing deck mid-fade.
 */
class PlaybackAudioFocus(
    context: Context,
    private val onHold: () -> Unit,
    private val onResume: () -> Unit,
) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var held = false
    private var resumeOnGain = false

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                resumeOnGain = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                onHold()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnGain) {
                    resumeOnGain = false
                    onResume()
                }
            }
        }
    }

    private val request: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= 26) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
        } else {
            null
        }

    fun request(): Boolean {
        val ok = if (Build.VERSION.SDK_INT >= 26 && request != null) {
            audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        held = ok
        return ok
    }

    fun abandon() {
        if (!held) return
        held = false
        if (Build.VERSION.SDK_INT >= 26 && request != null) {
            audio.abandonAudioFocusRequest(request)
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(listener)
        }
    }
}
