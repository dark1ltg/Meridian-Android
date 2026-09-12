package io.github.dark1ltg.meridian.analysis

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Listen-window decoder: MediaCodec first, Media3 FFmpeg if the platform codec cannot
 * open the file. Output is mono float32 little-endian at 11025 Hz (same as desktop ffmpeg).
 */
class PcmDecoder {
    private val abort = AtomicBoolean(false)

    fun decodeToF32le(path: String, startS: Double, durationS: Double): ByteArray? {
        if (abort.get()) return null
        val pcm = decodeMediaCodec(path, startS, durationS)
            ?: FfmpegPcmDecoder.decode(path, startS, durationS, abort)
            ?: return null
        if (pcm.size < 2048) return null
        return AudioResampler.floatsToF32le(pcm)
    }

    fun requestAbort() {
        abort.set(true)
    }

    fun clearAbort() {
        abort.set(false)
    }

    private fun decodeMediaCodec(path: String, startS: Double, durationS: Double): FloatArray? {
        val extractor = io.github.dark1ltg.meridian.data.PathProbe.openExtractor(path)
        var codec: MediaCodec? = null
        try {
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val startUs = (startS * 1_000_000.0).toLong().coerceAtLeast(0L)
            val endUs = startUs + (durationS * 1_000_000.0).toLong().coerceAtLeast(2_000_000L)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            var inRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                AudioResampler.TARGET_RATE
            }
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }
            var data = FloatArray((inRate * durationS.coerceAtLeast(2.0) * channels).toInt().coerceAtLeast(4096))
            var size = 0
            fun append(src: FloatArray, from: Int) {
                val n = src.size - from
                if (n <= 0) return
                if (size + n > data.size) {
                    data = data.copyOf((size + n).coerceAtLeast(data.size * 2))
                }
                System.arraycopy(src, from, data, size, n)
                size += n
            }
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val deadline = System.nanoTime() + 20_000_000_000L
            while (!outputDone) {
                if (abort.get() || System.nanoTime() > deadline) return null
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex) ?: break
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val pts = extractor.sampleTime
                        if (sampleSize < 0 || (pts >= 0 && pts > endUs + 200_000)) {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, pts.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = codec.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            inRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && info.size > 0) {
                            val encoding = outputPcmEncoding(codec.outputFormat)
                            val chunk = when (encoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> AudioResampler.pcmFloatToFloat(outBuf, info.size)
                                AudioFormat.ENCODING_PCM_8BIT -> AudioResampler.pcm8ToFloat(outBuf, info.size)
                                else -> AudioResampler.pcm16ToFloat(outBuf, info.size)
                            }
                            val skip = if (info.presentationTimeUs < startUs && inRate > 0) {
                                val deltaSec = (startUs - info.presentationTimeUs) / 1_000_000.0
                                (deltaSec * inRate * channels).toInt().coerceIn(0, chunk.size)
                            } else {
                                0
                            }
                            append(chunk, skip)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                        if (info.presentationTimeUs >= endUs) {
                            outputDone = true
                        }
                    }
                }
            }
            if (size < 2048) return null
            val mono = AudioResampler.downmixToMono(data.copyOf(size), channels)
            return AudioResampler.resample(mono, inRate)
        } catch (_: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }
}

private fun outputPcmEncoding(format: MediaFormat): Int {
    if (Build.VERSION.SDK_INT >= 24 && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        return format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    }
    return AudioFormat.ENCODING_PCM_16BIT
}
