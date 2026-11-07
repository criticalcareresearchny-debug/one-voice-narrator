// Native Shine MP3 encoder. Sources: https://github.com/toots/shine
// One Voice uses 24 kHz mono / 96 kbps to match the web narrator.
#include <jni.h>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <vector>
extern "C" {
#include "layer3.h"
}

struct Encoder {
    shine_t shine = nullptr;
    FILE* file = nullptr;
    std::vector<int16_t> frame;
    size_t filled = 0;
    bool error = false;
    int frameSize = 0;
};

static bool encode(Encoder* state) {
    int written = 0;
    unsigned char* data = shine_encode_buffer_interleaved(state->shine, state->frame.data(), &written);
    if (written < 0 || (written > 0 && (!data || fwrite(data, 1, written, state->file) != (size_t)written))) {
        state->error = true;
        return false;
    }
    state->filled = 0;
    return true;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_onevoice_nativeapp_NativeMp3_openNative(JNIEnv* env, jobject, jstring target, jint sampleRate, jint bitrate) {
    if (!target || shine_check_config(sampleRate, bitrate) < 0) return 0;
    const char* filename = env->GetStringUTFChars(target, nullptr);
    if (!filename) return 0;
    FILE* out = fopen(filename, "wb");
    env->ReleaseStringUTFChars(target, filename);
    if (!out) return 0;
    shine_config_t config{};
    shine_set_config_mpeg_defaults(&config.mpeg);
    config.wave.channels = PCM_MONO;
    config.wave.samplerate = sampleRate;
    config.mpeg.mode = MONO;
    config.mpeg.bitr = bitrate;
    shine_t shine = shine_initialise(&config);
    if (!shine) {
        fclose(out);
        return 0;
    }
    auto state = std::make_unique<Encoder>();
    state->shine = shine;
    state->file = out;
    state->frameSize = shine_samples_per_pass(shine);
    if (state->frameSize <= 0 || state->frameSize > SHINE_MAX_SAMPLES) {
        shine_close(shine);
        fclose(out);
        return 0;
    }
    state->frame.resize(static_cast<size_t>(state->frameSize));
    return reinterpret_cast<jlong>(state.release());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onevoice_nativeapp_NativeMp3_writeNative(JNIEnv* env, jobject, jlong ptr, jshortArray array) {
    auto* state = reinterpret_cast<Encoder*>(ptr);
    if (!state || !array || state->error) return JNI_FALSE;
    const auto len = env->GetArrayLength(array);
    jshort* data = env->GetShortArrayElements(array, nullptr);
    if (!data) return JNI_FALSE;
    bool ok = true;
    for (jsize i = 0; i < len; ++i) {
        state->frame[state->filled++] = static_cast<int16_t>(data[i]);
        if (state->filled == state->frame.size() && !encode(state)) {
            ok = false;
            break;
        }
    }
    env->ReleaseShortArrayElements(array, data, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_onevoice_nativeapp_NativeMp3_closeNative(JNIEnv*, jobject, jlong ptr) {
    std::unique_ptr<Encoder> state(reinterpret_cast<Encoder*>(ptr));
    if (!state) return JNI_FALSE;
    bool ok = !state->error;
    if (ok && state->filled) {
        for (size_t i = state->filled; i < state->frame.size(); ++i) state->frame[i] = 0;
        ok = encode(state.get());
    }
    int written = 0;
    unsigned char* data = shine_flush(state->shine, &written);
    if (written < 0 || (written > 0 && (!data || fwrite(data, 1, written, state->file) != (size_t)written))) ok = false;
    shine_close(state->shine);
    if (fclose(state->file) != 0) ok = false;
    return ok ? JNI_TRUE : JNI_FALSE;
}
