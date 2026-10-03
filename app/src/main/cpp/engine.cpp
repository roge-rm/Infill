// The device end of the sound engine: an Oboe stream feeding the synth, and the JNI Kotlin calls.
#include <jni.h>
#include <android/log.h>
#include <oboe/Oboe.h>

#include <memory>
#include <mutex>

#include "synth/synth.h"

namespace {

constexpr const char* kTag = "InfillAudio";

class Engine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    bool start(int voiceBudget) {
        std::lock_guard<std::mutex> lock(mutex_);
        budget_ = voiceBudget;
        return open();
    }

    void stop() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (stream_) {
            stream_->stop();
            stream_->close();
            stream_.reset();
        }
    }

    void pause(bool paused) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!stream_) return;
        if (paused) stream_->requestPause(); else stream_->requestStart();
    }

    infill::Synth* synth() { return synth_.get(); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        synth_->render(static_cast<float*>(data), frames);
        return oboe::DataCallbackResult::Continue;
    }

    // The device changed (headphones, Bluetooth), so open a new stream.
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result error) override {
        __android_log_print(ANDROID_LOG_INFO, kTag, "stream closed (%s), reopening", oboe::convertToText(error));
        std::lock_guard<std::mutex> lock(mutex_);
        stream_.reset();
        open();
    }

private:
    bool open() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setUsage(oboe::Usage::Game)
            ->setContentType(oboe::ContentType::Sonification)
            ->setDataCallback(this)
            ->setErrorCallback(this);
        oboe::Result result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            __android_log_print(ANDROID_LOG_WARN, kTag, "could not open a stream: %s", oboe::convertToText(result));
            return false;
        }
        // Made once at the first stream's rate and kept: the game thread holds it across device
        // changes.
        if (!synth_) synth_ = std::make_unique<infill::Synth>(static_cast<float>(stream_->getSampleRate()), budget_);
        // Four bursts of slack, so a stalled frame doesn't run the buffer dry and crackle.
        stream_->setBufferSizeInFrames(stream_->getFramesPerBurst() * 4);
        result = stream_->requestStart();
        __android_log_print(ANDROID_LOG_INFO, kTag, "stream %d Hz, burst %d, %s", stream_->getSampleRate(),
                            stream_->getFramesPerBurst(), oboe::convertToText(result));
        return result == oboe::Result::OK;
    }

    std::mutex mutex_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::unique_ptr<infill::Synth> synth_;
    int budget_ = 32;
};

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
