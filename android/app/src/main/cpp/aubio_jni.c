#include <jni.h>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "aubio.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jbyteArray JNICALL
Java_io_github_dark1ltg_meridian_analysis_AubioBridge_nativeAnalyze(
    JNIEnv *env, jobject thiz, jbyteArray pcm_le, jint sample_rate) {
  if (pcm_le == NULL || sample_rate <= 0) {
    return NULL;
  }
  jsize nbytes = (*env)->GetArrayLength(env, pcm_le);
  if (nbytes < 2048 * 4) {
    return NULL;
  }
  const uint32_t nfloat = (uint32_t)(nbytes / 4);
  float *mono = (float *)malloc((size_t)nfloat * sizeof(float));
  if (!mono) {
    return NULL;
  }
  jbyte *raw = (*env)->GetByteArrayElements(env, pcm_le, NULL);
  if (!raw) {
    free(mono);
    return NULL;
  }
  memcpy(mono, raw, (size_t)nfloat * sizeof(float));
  (*env)->ReleaseByteArrayElements(env, pcm_le, raw, JNI_ABORT);

  const uint_t win_s = 512;
  const uint_t hop_s = 256;
  const uint_t sr = (uint_t)sample_rate;
  aubio_tempo_t *tempo = new_aubio_tempo("default", win_s, hop_s, sr);
  aubio_onset_t *onset = new_aubio_onset("default", win_s, hop_s, sr);
  fvec_t *in = new_fvec(hop_s);
  fvec_t *out_on = new_fvec(1);
  fvec_t *out_t = new_fvec(1);
  if (!tempo || !onset || !in || !out_on || !out_t) {
    if (tempo) del_aubio_tempo(tempo);
    if (onset) del_aubio_onset(onset);
    if (in) del_fvec(in);
    if (out_on) del_fvec(out_on);
    if (out_t) del_fvec(out_t);
    free(mono);
    return NULL;
  }

  uint_t n_onsets = 0;
  uint_t cap = 256;
  float *times = (float *)malloc((size_t)cap * sizeof(float));
  if (!times) {
    del_aubio_tempo(tempo);
    del_aubio_onset(onset);
    del_fvec(in);
    del_fvec(out_on);
    del_fvec(out_t);
    free(mono);
    return NULL;
  }

  for (uint_t i = 0; i + hop_s <= nfloat; i += hop_s) {
    for (uint_t h = 0; h < hop_s; h++) {
      in->data[h] = mono[i + h];
    }
    aubio_onset_do(onset, in, out_on);
    aubio_tempo_do(tempo, in, out_t);
    if (out_on->data[0] != 0) {
      if (n_onsets == cap) {
        cap *= 2;
        float *grown = (float *)realloc(times, (size_t)cap * sizeof(float));
        if (!grown) break;
        times = grown;
      }
      times[n_onsets++] = (float)i / (float)sr;
    }
  }

  float bpm = aubio_tempo_get_bpm(tempo);
  float duration_s = (float)nfloat / (float)sr;
  if (duration_s < 1e-3f) duration_s = 1e-3f;
  float rate = (float)n_onsets / duration_s;
  float kinetic = (rate - 0.35f) / 3.4f;
  if (kinetic < 0.05f) kinetic = 0.05f;
  if (kinetic > 0.95f) kinetic = 0.95f;
  float burstiness = 0.f;
  float consistency = 0.5f;
  if (n_onsets >= 3) {
    double mu = 0.0;
    uint_t nint = 0;
    for (uint_t k = 1; k < n_onsets; k++) {
      float d = times[k] - times[k - 1];
      if (d > 1e-4f) {
        mu += d;
        nint++;
      }
    }
    if (nint >= 2) {
      mu /= (double)nint;
      double var = 0.0;
      uint_t n2 = 0;
      for (uint_t k = 1; k < n_onsets; k++) {
        float d = times[k] - times[k - 1];
        if (d > 1e-4f) {
          double dev = d - mu;
          var += dev * dev;
          n2++;
        }
      }
      double sig = sqrt(var / (double)n2);
      burstiness = (float)(sig / (mu + 1e-6));
      if (burstiness > 2.f) burstiness = 2.f;
      burstiness /= 2.f;
      consistency = 1.f - burstiness;
      if (consistency < 0.f) consistency = 0.f;
    }
  }
  if (!(bpm >= 40.f && bpm <= 220.f) || n_onsets < 3 || rate < 0.25f || !isfinite(bpm)) {
    bpm = 0.f;
  }

  const uint32_t header = 6;
  const uint32_t total = header + n_onsets;
  jfloatArray out = (*env)->NewFloatArray(env, (jsize)total);
  if (out) {
    float hdr[6];
    hdr[0] = bpm;
    hdr[1] = (float)n_onsets;
    hdr[2] = kinetic;
    hdr[3] = rate;
    hdr[4] = burstiness;
    hdr[5] = consistency;
    (*env)->SetFloatArrayRegion(env, out, 0, 6, hdr);
    if (n_onsets > 0) {
      (*env)->SetFloatArrayRegion(env, out, 6, (jsize)n_onsets, times);
    }
  }

  del_aubio_tempo(tempo);
  del_aubio_onset(onset);
  del_fvec(in);
  del_fvec(out_on);
  del_fvec(out_t);
  free(times);
  free(mono);

  if (!out) return NULL;
  jsize flen = (*env)->GetArrayLength(env, out);
  jsize blen = flen * 4;
  jbyteArray packed = (*env)->NewByteArray(env, blen);
  if (!packed) return NULL;
  jfloat *fdata = (*env)->GetFloatArrayElements(env, out, NULL);
  (*env)->SetByteArrayRegion(env, packed, 0, blen, (jbyte *)fdata);
  (*env)->ReleaseFloatArrayElements(env, out, fdata, JNI_ABORT);
  return packed;
}

#ifdef __cplusplus
}
#endif
