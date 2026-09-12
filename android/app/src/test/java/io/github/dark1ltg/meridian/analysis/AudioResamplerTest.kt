package io.github.dark1ltg.meridian.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioResamplerTest {
    @Test
    fun downmixAveragesChannels() {
        val interleaved = floatArrayOf(1f, 3f, 2f, 4f)
        val mono = AudioResampler.downmixToMono(interleaved, 2)
        assertEquals(2, mono.size)
        assertEquals(2f, mono[0], 1e-5f)
        assertEquals(3f, mono[1], 1e-5f)
    }

    @Test
    fun resampleDoublesLengthWhenHalvingRate() {
        val input = FloatArray(100) { it.toFloat() }
        val out = AudioResampler.resample(input, inputRate = 22050, outputRate = 11025)
        assertEquals(50, out.size)
        assertEquals(0f, out[0], 0.01f)
    }

    @Test
    fun pcm16LittleEndianToFloat() {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(32767)
        buf.putShort(-32768)
        buf.flip()
        val samples = AudioResampler.pcm16ToFloat(buf, 4)
        assertEquals(2, samples.size)
        assertTrue(samples[0] > 0.99f)
        assertTrue(samples[1] <= -1f)
    }

    @Test
    fun f32leRoundTrip() {
        val src = floatArrayOf(-0.5f, 0f, 0.25f)
        val bytes = AudioResampler.floatsToF32le(src)
        assertEquals(12, bytes.size)
        val restored = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(-0.5f, restored.float, 1e-6f)
        assertEquals(0f, restored.float, 1e-6f)
        assertEquals(0.25f, restored.float, 1e-6f)
    }
}
