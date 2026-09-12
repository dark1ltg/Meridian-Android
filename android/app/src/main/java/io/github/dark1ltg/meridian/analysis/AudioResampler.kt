package io.github.dark1ltg.meridian.analysis

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object AudioResampler {
    const val TARGET_RATE = 11025

    fun downmixToMono(interleaved: FloatArray, channels: Int): FloatArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        val out = FloatArray(frames)
        var i = 0
        var f = 0
        while (f < frames) {
            var sum = 0f
            var c = 0
            while (c < channels) {
                sum += interleaved[i++]
                c++
            }
            out[f] = sum / channels
            f++
        }
        return out
    }

    fun resample(input: FloatArray, inputRate: Int, outputRate: Int = TARGET_RATE): FloatArray {
        if (input.isEmpty() || inputRate <= 0) return input
        if (inputRate == outputRate) return input
        val outLen = maxOf(1, (input.size.toLong() * outputRate / inputRate).toInt())
        val out = FloatArray(outLen)
        val step = inputRate.toDouble() / outputRate.toDouble()
        var pos = 0.0
        for (i in 0 until outLen) {
            val idx = pos.toInt().coerceIn(0, input.lastIndex)
            val frac = (pos - idx).toFloat()
            val next = min(idx + 1, input.lastIndex)
            out[i] = input[idx] * (1f - frac) + input[next] * frac
            pos += step
        }
        return out
    }

    fun pcm16ToFloat(buffer: ByteBuffer, bytes: Int): FloatArray {
        val samples = bytes / 2
        val out = FloatArray(samples)
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i < samples && dup.remaining() >= 2) {
            out[i] = dup.short / 32768f
            i++
        }
        return out
    }

    fun pcmFloatToFloat(buffer: ByteBuffer, bytes: Int): FloatArray {
        val samples = bytes / 4
        val out = FloatArray(samples)
        val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i < samples && dup.remaining() >= 4) {
            out[i] = dup.float
            i++
        }
        return out
    }

    fun pcm8ToFloat(buffer: ByteBuffer, bytes: Int): FloatArray {
        val out = FloatArray(bytes)
        val dup = buffer.duplicate()
        var i = 0
        while (i < bytes && dup.hasRemaining()) {
            out[i] = ((dup.get().toInt() and 0xFF) - 128) / 128f
            i++
        }
        return out
    }

    fun floatsToF32le(samples: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putFloat(it) }
        return buf.array()
    }
}
