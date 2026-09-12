package io.github.dark1ltg.meridian.analysis

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Media3 FFmpeg decoder (same module as FfmpegAudioRenderer). Used when MediaCodec
 * cannot open the file. Requires bundled FFmpeg JNI libs; otherwise this is a no-op.
 */
@UnstableApi
object FfmpegPcmDecoder {
    private const val TAG = "FfmpegPcm"

    fun isAvailable(): Boolean = try {
        FfmpegLibrary.isAvailable()
    } catch (_: Throwable) {
        false
    }

    fun decode(
        path: String,
        startS: Double,
        durationS: Double,
        abort: AtomicBoolean,
    ): FloatArray? {
        if (!isAvailable()) return null
        val extractor = io.github.dark1ltg.meridian.data.PathProbe.openExtractor(path)
        var decoder: FfmpegAudioDecoder? = null
        try {
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val mediaFormat = extractor.getTrackFormat(track)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME) ?: return null
            if (!FfmpegLibrary.supportsFormat(mime)) return null
            val sampleRate = if (mediaFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                AudioResampler.TARGET_RATE
            }
            val channels = if (mediaFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }
            val initData = ArrayList<ByteArray>()
            var csd = 0
            while (true) {
                val key = "csd-$csd"
                if (!mediaFormat.containsKey(key)) break
                val buf = mediaFormat.getByteBuffer(key) ?: break
                val copy = ByteArray(buf.remaining())
                buf.mark()
                buf.get(copy)
                buf.reset()
                initData.add(copy)
                csd++
            }
            val format = Format.Builder()
                .setSampleMimeType(mime)
                .setChannelCount(channels)
                .setSampleRate(sampleRate)
                .setPcmEncoding(C.ENCODING_PCM_FLOAT)
                .setInitializationData(initData)
                .build()
            val maxInput = if (mediaFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                65_536
            }
            decoder = FfmpegAudioDecoder(
                format,
                /* numInputBuffers = */ 16,
                /* numOutputBuffers = */ 16,
                maxInput,
                /* outputFloat = */ true,
            )
            val startUs = (startS * 1_000_000.0).toLong().coerceAtLeast(0L)
            val endUs = startUs + (durationS * 1_000_000.0).toLong().coerceAtLeast(2_000_000L)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var data = FloatArray((sampleRate * durationS.coerceAtLeast(2.0) * channels).toInt().coerceAtLeast(4096))
            var size = 0
            fun append(src: FloatArray, from: Int) {
                val n = src.size - from
                if (n <= 0) return
                if (size + n > data.size) data = data.copyOf((size + n).coerceAtLeast(data.size * 2))
                System.arraycopy(src, from, data, size, n)
                size += n
            }
            val deadline = System.nanoTime() + 20_000_000_000L
            var inputEnded = false
            var idle = 0
            while (true) {
                if (abort.get() || System.nanoTime() > deadline) return null
                var progressed = false
                if (!inputEnded) {
                    val input = decoder.dequeueInputBuffer()
                    if (input != null) {
                        progressed = true
                        input.clear()
                        val inBuf = input.data
                        if (inBuf == null) {
                            decoder.queueInputBuffer(input)
                        } else {
                            inBuf.clear()
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            val pts = extractor.sampleTime
                            if (sampleSize < 0 || (pts >= 0 && pts > endUs + 200_000)) {
                                input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                                input.timeUs = 0
                                decoder.queueInputBuffer(input)
                                inputEnded = true
                            } else {
                                inBuf.limit(sampleSize)
                                input.timeUs = pts.coerceAtLeast(0L)
                                decoder.queueInputBuffer(input)
                                extractor.advance()
                            }
                        }
                    }
                }
                val output = decoder.dequeueOutputBuffer() as SimpleDecoderOutputBuffer?
                if (output != null) {
                    progressed = true
                    idle = 0
                    val buf: ByteBuffer? = output.data
                    if (buf != null && buf.remaining() > 0) {
                        val chunk = AudioResampler.pcmFloatToFloat(buf, buf.remaining())
                        val skip = if (output.timeUs < startUs && sampleRate > 0) {
                            val deltaSec = (startUs - output.timeUs) / 1_000_000.0
                            (deltaSec * sampleRate * channels).toInt().coerceIn(0, chunk.size)
                        } else {
                            0
                        }
                        append(chunk, skip)
                    }
                    val ended = output.isEndOfStream
                    val late = output.timeUs >= endUs
                    output.release()
                    if (ended || late) break
                } else if (inputEnded) {
                    idle++
                    if (idle > 8) break
                }
                if (!progressed && !inputEnded) {
                    idle++
                    if (idle > 200) break
                }
            }
            if (size < 2048) return null
            val mono = AudioResampler.downmixToMono(data.copyOf(size), channels)
            return AudioResampler.resample(mono, sampleRate)
        } catch (exc: Throwable) {
            Log.w(TAG, "FFmpeg listen decode failed: ${exc.message}")
            return null
        } finally {
            try {
                decoder?.release()
            } catch (_: Exception) {
            }
            extractor.release()
        }
    }
}
