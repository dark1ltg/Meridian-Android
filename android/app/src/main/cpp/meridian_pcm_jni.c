/*
 * Desktop-shaped Listen decode: seek → mono float32 LE @ 11025 Hz via
 * libavformat + libavcodec + libswresample (same contract as host ffmpeg
 * -ac 1 -ar 11025 -f f32le). Built into libmeridian_pcm.so by
 * android/scripts/build-media3-ffmpeg.sh.
 */
#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/frame.h>
#include <libavutil/opt.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>

#define LOG_TAG "MeridianPcm"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define TARGET_RATE 11025
#define MAX_OUT_SECONDS 40.0

typedef struct MeridianPcmSession {
    AVFormatContext *fmt;
    AVCodecContext *codec;
    SwrContext *swr;
    int stream_index;
    int fd_owned; /* close fd when session ends (dup'd content:// FD) */
    int fd;
} MeridianPcmSession;

static int read_fd_packet(void *opaque, uint8_t *buf, int buf_size) {
    MeridianPcmSession *s = (MeridianPcmSession *)opaque;
    ssize_t n = read(s->fd, buf, (size_t)buf_size);
    if (n == 0) return AVERROR_EOF;
    if (n < 0) return AVERROR(errno);
    return (int)n;
}

static int64_t seek_fd(void *opaque, int64_t offset, int whence) {
    MeridianPcmSession *s = (MeridianPcmSession *)opaque;
    if (whence == AVSEEK_SIZE) {
        off_t cur = lseek(s->fd, 0, SEEK_CUR);
        off_t end = lseek(s->fd, 0, SEEK_END);
        if (cur < 0 || end < 0) return -1;
        lseek(s->fd, cur, SEEK_SET);
        return end;
    }
    off_t r = lseek(s->fd, (off_t)offset, whence);
    return r < 0 ? -1 : r;
}

static void session_free(MeridianPcmSession *s) {
    if (!s) return;
    if (s->swr) swr_free(&s->swr);
    if (s->codec) avcodec_free_context(&s->codec);
    if (s->fmt) {
        if (s->fmt->pb && s->fd_owned) {
            avio_context_free(&s->fmt->pb);
        }
        avformat_close_input(&s->fmt);
    }
    if (s->fd_owned && s->fd >= 0) close(s->fd);
    free(s);
}

static int open_codec(MeridianPcmSession *s) {
    const AVCodec *dec = NULL;
    s->stream_index = av_find_best_stream(s->fmt, AVMEDIA_TYPE_AUDIO, -1, -1, &dec, 0);
    if (s->stream_index < 0 || !dec) return -1;
    s->codec = avcodec_alloc_context3(dec);
    if (!s->codec) return -1;
    if (avcodec_parameters_to_context(s->codec, s->fmt->streams[s->stream_index]->codecpar) < 0) {
        return -1;
    }
    if (avcodec_open2(s->codec, dec, NULL) < 0) return -1;

    AVChannelLayout out_ch;
    av_channel_layout_default(&out_ch, 1);
    s->swr = swr_alloc();
    if (!s->swr) return -1;
    av_opt_set_chlayout(s->swr, "in_chlayout", &s->codec->ch_layout, 0);
    av_opt_set_int(s->swr, "in_sample_rate", s->codec->sample_rate, 0);
    av_opt_set_sample_fmt(s->swr, "in_sample_fmt", s->codec->sample_fmt, 0);
    av_opt_set_chlayout(s->swr, "out_chlayout", &out_ch, 0);
    av_opt_set_int(s->swr, "out_sample_rate", TARGET_RATE, 0);
    av_opt_set_sample_fmt(s->swr, "out_sample_fmt", AV_SAMPLE_FMT_FLT, 0);
    av_channel_layout_uninit(&out_ch);
    if (swr_init(s->swr) < 0) return -1;
    return 0;
}

static MeridianPcmSession *session_open_path(const char *path) {
    MeridianPcmSession *s = calloc(1, sizeof(*s));
    if (!s) return NULL;
    s->fd = -1;
    if (avformat_open_input(&s->fmt, path, NULL, NULL) < 0) {
        session_free(s);
        return NULL;
    }
    if (avformat_find_stream_info(s->fmt, NULL) < 0 || open_codec(s) < 0) {
        session_free(s);
        return NULL;
    }
    return s;
}

static MeridianPcmSession *session_open_fd(int fd) {
    MeridianPcmSession *s = calloc(1, sizeof(*s));
    if (!s) return NULL;
    s->fd = fd;
    s->fd_owned = 1;
    uint8_t *iobuf = av_malloc(1 << 15);
    if (!iobuf) {
        session_free(s);
        return NULL;
    }
    AVIOContext *avio = avio_alloc_context(iobuf, 1 << 15, 0, s, read_fd_packet, NULL, seek_fd);
    if (!avio) {
        av_free(iobuf);
        session_free(s);
        return NULL;
    }
    s->fmt = avformat_alloc_context();
    if (!s->fmt) {
        avio_context_free(&avio);
        session_free(s);
        return NULL;
    }
    s->fmt->pb = avio;
    if (avformat_open_input(&s->fmt, NULL, NULL, NULL) < 0) {
        /* avformat_open_input frees fmt on failure in some paths — guard */
        s->fmt = NULL;
        avio_context_free(&avio);
        session_free(s);
        return NULL;
    }
    if (avformat_find_stream_info(s->fmt, NULL) < 0 || open_codec(s) < 0) {
        session_free(s);
        return NULL;
    }
    return s;
}

static jbyteArray decode_window(JNIEnv *env, MeridianPcmSession *s, double start_s, double duration_s) {
    if (!s || !s->fmt || !s->codec || !s->swr) return NULL;
    if (duration_s < 0.5) duration_s = 0.5;
    if (duration_s > MAX_OUT_SECONDS) duration_s = MAX_OUT_SECONDS;
    if (start_s < 0) start_s = 0;

    AVStream *st = s->fmt->streams[s->stream_index];
    int64_t start_tb = (int64_t)(start_s * (double)st->time_base.den / (double)st->time_base.num);
    avcodec_flush_buffers(s->codec);
    if (av_seek_frame(s->fmt, s->stream_index, start_tb, AVSEEK_FLAG_BACKWARD) < 0) {
        /* try file-level seek in microseconds */
        int64_t ts = (int64_t)(start_s * AV_TIME_BASE);
        if (avformat_seek_file(s->fmt, -1, INT64_MIN, ts, INT64_MAX, 0) < 0) {
            return NULL;
        }
        avcodec_flush_buffers(s->codec);
    }

    const int max_out = (int)(TARGET_RATE * duration_s) + TARGET_RATE;
    float *out = (float *)malloc((size_t)max_out * sizeof(float));
    if (!out) return NULL;
    int out_n = 0;
    const int64_t end_pts = (int64_t)((start_s + duration_s) * (double)st->time_base.den /
                                      (double)st->time_base.num);

    AVPacket *pkt = av_packet_alloc();
    AVFrame *frame = av_frame_alloc();
    if (!pkt || !frame) {
        free(out);
        if (pkt) av_packet_free(&pkt);
        if (frame) av_frame_free(&frame);
        return NULL;
    }

    int got_enough = 0;
    while (!got_enough && av_read_frame(s->fmt, pkt) >= 0) {
        if (pkt->stream_index != s->stream_index) {
            av_packet_unref(pkt);
            continue;
        }
        if (avcodec_send_packet(s->codec, pkt) < 0) {
            av_packet_unref(pkt);
            break;
        }
        av_packet_unref(pkt);
        while (avcodec_receive_frame(s->codec, frame) == 0) {
            int64_t pts = frame->best_effort_timestamp;
            if (pts != AV_NOPTS_VALUE && pts + frame->nb_samples < start_tb) {
                av_frame_unref(frame);
                continue;
            }
            uint8_t *planes[1];
            int out_capacity = max_out - out_n;
            if (out_capacity <= 0) {
                got_enough = 1;
                av_frame_unref(frame);
                break;
            }
            planes[0] = (uint8_t *)(out + out_n);
            int converted = swr_convert(
                s->swr,
                planes,
                out_capacity,
                (const uint8_t **)frame->extended_data,
                frame->nb_samples);
            if (converted > 0) {
                /* Drop early samples before start_s when seek landed early. */
                int skip = 0;
                if (pts != AV_NOPTS_VALUE && pts < start_tb && s->codec->sample_rate > 0) {
                    double early = (double)(start_tb - pts) * st->time_base.num / st->time_base.den;
                    skip = (int)(early * TARGET_RATE);
                    if (skip > converted) skip = converted;
                }
                if (skip > 0) {
                    memmove(out + out_n, out + out_n + skip, (size_t)(converted - skip) * sizeof(float));
                    converted -= skip;
                }
                out_n += converted;
            }
            if (pts != AV_NOPTS_VALUE && pts >= end_pts) got_enough = 1;
            if (out_n >= (int)(TARGET_RATE * duration_s)) got_enough = 1;
            av_frame_unref(frame);
            if (got_enough) break;
        }
    }
    /* Drain */
    avcodec_send_packet(s->codec, NULL);
    while (!got_enough && avcodec_receive_frame(s->codec, frame) == 0) {
        uint8_t *planes[1] = {(uint8_t *)(out + out_n)};
        int out_capacity = max_out - out_n;
        if (out_capacity <= 0) break;
        int converted = swr_convert(
            s->swr, planes, out_capacity, (const uint8_t **)frame->extended_data, frame->nb_samples);
        if (converted > 0) out_n += converted;
        av_frame_unref(frame);
        if (out_n >= (int)(TARGET_RATE * duration_s * 0.9)) break;
    }

    av_packet_free(&pkt);
    av_frame_free(&frame);

    if (out_n < 512) {
        free(out);
        return NULL;
    }
    jbyteArray arr = (*env)->NewByteArray(env, out_n * (jsize)sizeof(float));
    if (!arr) {
        free(out);
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, arr, 0, out_n * (jsize)sizeof(float), (const jbyte *)out);
    free(out);
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_io_github_dark1ltg_meridian_analysis_NativeListenPcm_nativeAvailable(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_io_github_dark1ltg_meridian_analysis_NativeListenPcm_nativeOpenPath(
    JNIEnv *env, jclass clazz, jstring jpath) {
    (void)clazz;
    if (!jpath) return 0;
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (!path) return 0;
    MeridianPcmSession *s = session_open_path(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (jlong)(intptr_t)s;
}

JNIEXPORT jlong JNICALL
Java_io_github_dark1ltg_meridian_analysis_NativeListenPcm_nativeOpenFd(
    JNIEnv *env, jclass clazz, jint fd) {
    (void)env;
    (void)clazz;
    /* Java ParcelFileDescriptor.detachFd() — we own and close this fd. */
    if (fd < 0) return 0;
    MeridianPcmSession *s = session_open_fd(fd);
    return (jlong)(intptr_t)s;
}

JNIEXPORT jbyteArray JNICALL
Java_io_github_dark1ltg_meridian_analysis_NativeListenPcm_nativeDecodeWindow(
    JNIEnv *env, jclass clazz, jlong handle, jdouble start_s, jdouble duration_s) {
    (void)clazz;
    MeridianPcmSession *s = (MeridianPcmSession *)(intptr_t)handle;
    return decode_window(env, s, start_s, duration_s);
}

JNIEXPORT void JNICALL
Java_io_github_dark1ltg_meridian_analysis_NativeListenPcm_nativeClose(
    JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    session_free((MeridianPcmSession *)(intptr_t)handle);
}
