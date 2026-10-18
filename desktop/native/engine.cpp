// The device end of the sound engine on Linux and Windows: a miniaudio playback device feeding
// the synth. jni.cpp (in app/src/main/cpp) has the calls Kotlin makes.
#define MINIAUDIO_IMPLEMENTATION
#define MA_NO_DECODING
#define MA_NO_ENCODING
#define MA_NO_GENERATION
#define MA_NO_RESOURCE_MANAGER
#define MA_NO_NODE_GRAPH
#define MA_NO_ENGINE
#include <miniaudio.h>

#include <cstdio>
#include <memory>
#include <mutex>

#include <jni.h>
#include "synth/synth.h"

namespace {

/** 256 frames, 5.3 ms at 48 kHz, in three periods: about what a phone's four bursts give. */
constexpr ma_uint32 kPeriodFrames = 256;
constexpr ma_uint32 kPeriods = 3;

void onOutput(ma_device* device, void* out, const void*, ma_uint32 frames);

class Engine {
public:
    bool start(int voiceBudget) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (open_) return true;
        ma_device_config config = ma_device_config_init(ma_device_type_playback);
        config.playback.format = ma_format_f32;
        config.playback.channels = 2;
        config.sampleRate = 0;
        config.periodSizeInFrames = kPeriodFrames;
        config.periods = kPeriods;
        config.dataCallback = onOutput;
        config.pUserData = this;
        if (ma_device_init(nullptr, &config, &device_) != MA_SUCCESS) {
            std::fprintf(stderr, "W InfillAudio: could not open an output\n");
            return false;
        }
        // Made once at the first device's rate and kept, as on Android.
        if (!synth_) synth_ = std::make_unique<infill::Synth>(static_cast<float>(device_.sampleRate), voiceBudget);
        open_ = true;
        const bool started = ma_device_start(&device_) == MA_SUCCESS;
        std::fprintf(stderr, "I InfillAudio: %s, %u Hz, %s\n", ma_get_backend_name(device_.pContext->backend),
                     device_.sampleRate, started ? "started" : "not started");
        if (!started) { ma_device_uninit(&device_); open_ = false; }
        return started;
    }

    void stop() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!open_) return;
        ma_device_uninit(&device_);
        open_ = false;
    }

    void pause(bool paused) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!open_) return;
        if (paused) ma_device_stop(&device_); else ma_device_start(&device_);
    }

    infill::Synth* synth() { return synth_.get(); }

    void render(float* out, ma_uint32 frames) { synth_->render(out, static_cast<int32_t>(frames)); }

private:
    std::mutex mutex_;
    ma_device device_{};
    bool open_ = false;
    std::unique_ptr<infill::Synth> synth_;
};

void onOutput(ma_device* device, void* out, const void*, ma_uint32 frames) {
    static_cast<Engine*>(device->pUserData)->render(static_cast<float*>(out), frames);
}

Engine* engine = nullptr;

}  // namespace


extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_rm_infill_audio_Synth_nativeStart(JNIEnv*, jobject, jint voiceBudget) {
    if (!engine) engine = new Engine();
    return engine->start(voiceBudget) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_rm_infill_audio_Synth_nativeStop(JNIEnv*, jobject) {
    if (engine) engine->stop();
}

JNIEXPORT void JNICALL
Java_com_rm_infill_audio_Synth_nativePause(JNIEnv*, jobject, jboolean paused) {
    if (engine) engine->pause(paused == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_rm_infill_audio_Synth_nativeScene(JNIEnv* env, jobject, jint count, jintArray keys, jintArray recipes,
                                                 jintArray flags, jfloatArray params) {
    if (!engine || !engine->synth()) return;
    infill::Scene scene;
    scene.count = std::min(static_cast<int>(count), infill::kMaxSceneEntries);
    // Copy only the entries in use onto the stack, not the whole arrays.
    jint k[infill::kMaxSceneEntries];
    jint r[infill::kMaxSceneEntries];
    jint f[infill::kMaxSceneEntries];
    jfloat p[infill::kMaxSceneEntries * infill::kParams];
    if (scene.count > 0) {
        env->GetIntArrayRegion(keys, 0, scene.count, k);
        env->GetIntArrayRegion(recipes, 0, scene.count, r);
        env->GetIntArrayRegion(flags, 0, scene.count, f);
        env->GetFloatArrayRegion(params, 0, scene.count * infill::kParams, p);
    }
    for (int i = 0; i < scene.count; ++i) {
        scene.entries[i].key = k[i];
        scene.entries[i].recipe = r[i];
        scene.entries[i].flags = f[i];
        for (int j = 0; j < infill::kParams; ++j) scene.entries[i].p[j] = p[i * infill::kParams + j];
    }
    engine->synth()->publishScene(scene);
}

JNIEXPORT void JNICALL
Java_com_rm_infill_audio_Synth_nativeEvent(JNIEnv* env, jobject, jint recipe, jint flags, jint seed, jfloat delay,
                                                 jfloatArray params) {
    if (!engine || !engine->synth()) return;
    infill::Event event;
    event.recipe = recipe;
    event.flags = flags;
    event.seed = static_cast<uint32_t>(seed);
    event.delay = delay;
    jfloat* p = env->GetFloatArrayElements(params, nullptr);
    for (int j = 0; j < infill::kParams; ++j) event.p[j] = p[j];
    env->ReleaseFloatArrayElements(params, p, JNI_ABORT);
    engine->synth()->pushEvent(event);
}

JNIEXPORT void JNICALL
Java_com_rm_infill_audio_Synth_nativeBusGains(JNIEnv* env, jobject, jfloatArray gains) {
    if (!engine || !engine->synth()) return;
    jfloat* g = env->GetFloatArrayElements(gains, nullptr);
    float copy[infill::bus::COUNT];
    for (int b = 0; b < infill::bus::COUNT; ++b) copy[b] = g[b];
    env->ReleaseFloatArrayElements(gains, g, JNI_ABORT);
    engine->synth()->setBusGains(copy);
}

JNIEXPORT jint JNICALL
Java_com_rm_infill_audio_Synth_nativeActiveVoices(JNIEnv*, jobject) {
    return engine && engine->synth() ? engine->synth()->activeVoices() : 0;
}

JNIEXPORT void JNICALL
Java_com_rm_infill_audio_Synth_nativeRoom(JNIEnv*, jobject, jfloat amount) {
    if (engine && engine->synth()) engine->synth()->setRoom(amount);
}

}  // extern "C"
