package io.github.dark1ltg.meridian.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.dark1ltg.meridian.data.TrackRow
import java.io.File

object MediaItems {
    fun fromTrack(track: TrackRow): MediaItem {
        val uri = if (track.path.startsWith("content:")) {
            Uri.parse(track.path)
        } else {
            Uri.fromFile(File(track.path))
        }
        val meta = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkData(null, null)
            .setArtworkUri(null)
            .build()
        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(uri)
            .setMediaMetadata(meta)
            .build()
    }
}

fun MediaMetadata.withoutArtwork(): MediaMetadata {
    return buildUpon()
        .setArtworkData(null, null)
        .setArtworkUri(null)
        .build()
}
