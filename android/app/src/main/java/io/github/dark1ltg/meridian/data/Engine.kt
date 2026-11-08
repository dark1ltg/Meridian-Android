package io.github.dark1ltg.meridian.data

import com.chaquo.python.Python
import org.json.JSONObject

class Engine {
    private val bridge by lazy {
        Python.getInstance().getModule("meridian.android_bridge")
    }

    fun call(method: String, payload: JSONObject = JSONObject()): EngineSnapshot {
        // Heavy Scan/Listen must not hold the transport lock for the whole job —
        // otherwise play/skip/finished_current freeze until Analyze finishes.
        if (method in HEAVY) {
            synchronized(heavyLock) {
                val raw = bridge.callAttr("call", method, payload.toString()).toString()
                return JSONObject(raw).toSnapshot()
            }
        }
        synchronized(lock) {
            val raw = bridge.callAttr("call", method, payload.toString()).toString()
            return JSONObject(raw).toSnapshot()
        }
    }

    /** Peek job progress without taking the engine lock held by scan/Listen. */
    fun progress(): JobProgress {
        val raw = bridge.callAttr("call", "progress", "{}").toString()
        return JSONObject(raw).toJob()
    }

    fun abort() {
        bridge.callAttr("call", "request_abort", "{}")
    }

    companion object {
        private val lock = Any()
        private val heavyLock = Any()
        private val HEAVY = setOf("scan", "analyze", "rescan")
    }
}
