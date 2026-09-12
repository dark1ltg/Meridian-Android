package io.github.dark1ltg.meridian.analysis

object AubioBridge {
    @Volatile
    private var loaded = false

    @Volatile
    private var available = false

    @JvmStatic
    fun isAvailable(): Boolean {
        ensureLoaded()
        return available
    }

    @JvmStatic
    fun analyze(pcmF32le: ByteArray, sampleRate: Int): ByteArray? {
        ensureLoaded()
        if (!available || pcmF32le.size < 2048) return null
        return try {
            nativeAnalyze(pcmF32le, sampleRate)
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            available = try {
                System.loadLibrary("meridian_aubio")
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
            loaded = true
        }
    }

    private external fun nativeAnalyze(pcmF32le: ByteArray, sampleRate: Int): ByteArray?
}
