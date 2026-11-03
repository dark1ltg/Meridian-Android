package io.github.dark1ltg.meridian.analysis

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Desktop-shaped Listen decode via [libmeridian_pcm.so]: one open per file,
 * seek windows → mono float32 LE @ 11025 Hz (same contract as host ffmpeg).
 */
object NativeListenPcm {
    private const val TAG = "NativeListenPcm"

    @Volatile
    private var loadTried = false

    @Volatile
    private var loaded = false

    fun isAvailable(): Boolean {
        if (loadTried) return loaded
        synchronized(this) {
            if (loadTried) return loaded
            loadTried = true
            loaded = try {
                System.loadLibrary("meridian_pcm")
                nativeAvailable()
            } catch (t: Throwable) {
                Log.i(TAG, "libmeridian_pcm not loaded: ${t.message}")
                false
            }
            return loaded
        }
    }

    fun open(context: Context?, path: String): Long {
        if (!isAvailable()) return 0L
        val raw = path.trim()
        if (raw.isEmpty()) return 0L
        return if (raw.startsWith("content:")) {
            openContent(context, raw)
        } else {
            nativeOpenPath(raw)
        }
    }

    fun decodeWindow(handle: Long, startS: Double, durationS: Double): ByteArray? {
        if (handle == 0L || !isAvailable()) return null
        return try {
            nativeDecodeWindow(handle, startS, durationS)
        } catch (t: Throwable) {
            Log.w(TAG, "decodeWindow failed: ${t.message}")
            null
        }
    }

    fun close(handle: Long) {
        if (handle == 0L || !isAvailable()) return
        try {
            nativeClose(handle)
        } catch (_: Throwable) {
        }
    }

    private fun openContent(context: Context?, path: String): Long {
        val ctx = context ?: return 0L
        var pfd: ParcelFileDescriptor? = null
        return try {
            pfd = ctx.contentResolver.openFileDescriptor(Uri.parse(path), "r") ?: return 0L
            nativeOpenFd(pfd.detachFd())
        } catch (t: Throwable) {
            Log.w(TAG, "openContent failed: ${t.message}")
            0L
        } finally {
            try {
                pfd?.close()
            } catch (_: Throwable) {
            }
        }
    }

    private external fun nativeAvailable(): Boolean
    private external fun nativeOpenPath(path: String): Long
    private external fun nativeOpenFd(fd: Int): Long
    private external fun nativeDecodeWindow(handle: Long, startS: Double, durationS: Double): ByteArray?
    private external fun nativeClose(handle: Long)
}
