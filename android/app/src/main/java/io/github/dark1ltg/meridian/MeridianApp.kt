package io.github.dark1ltg.meridian

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import io.github.dark1ltg.meridian.analysis.PcmDecoder
import io.github.dark1ltg.meridian.data.PathProbe

class MeridianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PathProbe.app = this
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        try {
            val py = Python.getInstance()
            val decoder = PcmDecoder()
            decoder.clearAbort()
            py.getModule("meridian.features").callAttr("set_external_pcm_decoder", decoder)
            py.getModule("meridian.android_bridge").callAttr("set_path_probe", PathProbe(this))
        } catch (_: Exception) {
            // Engine still runs on tags if the hook cannot attach.
        }
    }
}
