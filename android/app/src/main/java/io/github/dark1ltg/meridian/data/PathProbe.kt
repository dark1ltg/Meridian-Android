package io.github.dark1ltg.meridian.data

import android.app.Application
import android.media.MediaExtractor
import android.net.Uri
import java.io.File

/** Lets Python and MediaCodec open both filesystem paths and SAF content URIs. */
class PathProbe(private val app: Application) {
    fun reachable(path: String): Boolean {
        val raw = path.trim()
        if (raw.isEmpty()) return false
        return if (raw.startsWith("content:")) {
            try {
                app.contentResolver.openAssetFileDescriptor(Uri.parse(raw), "r")?.use { fd ->
                    fd.length != 0L
                } ?: false
            } catch (_: Exception) {
                false
            }
        } else {
            File(raw).isFile
        }
    }

    companion object {
        @Volatile
        var app: Application? = null

        fun openExtractor(path: String): MediaExtractor {
            val extractor = MediaExtractor()
            val ctx = app
            if (path.startsWith("content:") && ctx != null) {
                extractor.setDataSource(ctx, Uri.parse(path), null)
            } else {
                extractor.setDataSource(path)
            }
            return extractor
        }
    }
}
