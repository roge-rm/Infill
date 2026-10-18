// Each music deck owns a Vorbis decoder and a miniaudio output. Stopping the device joins
// its callback before the decoder is freed; only the callback advances the stream.
#include <jni.h>
#include <miniaudio.h>
#include "stb_vorbis.c"
#include <atomic>
#include <cstring>
#include <vector>

namespace {
struct Deck {
    std::vector<unsigned char> bytes;
    stb_vorbis* decoder = nullptr;
    ma_device device{};
    bool open = false;
    std::atomic<float> volume{0};
    std::atomic<bool> ended{false};

    void stop() {
        if (open) { ma_device_uninit(&device); open = false; }
        if (decoder) { stb_vorbis_close(decoder); decoder = nullptr; }
        bytes.clear();
    }
    static void render(ma_device* device, void* output, const void*, ma_uint32 frames) {
        auto& deck = *static_cast<Deck*>(device->pUserData);
        auto* out = static_cast<float*>(output);
        const int read = stb_vorbis_get_samples_float_interleaved(deck.decoder, 2, out, frames * 2);
        const float gain = deck.volume.load();
        for (int i = 0; i < read * 2; ++i) out[i] *= gain;
        std::memset(out + read * 2, 0, (frames - read) * 2 * sizeof(float));
        if (read < static_cast<int>(frames)) deck.ended.store(true);
    }
    bool load(JNIEnv* env, jbyteArray data) {
        stop();
        bytes.resize(env->GetArrayLength(data));
        env->GetByteArrayRegion(data, 0, bytes.size(), reinterpret_cast<jbyte*>(bytes.data()));
        int error = 0;
        decoder = stb_vorbis_open_memory(bytes.data(), bytes.size(), &error, nullptr);
        if (!decoder) { stop(); return false; }
        auto config = ma_device_config_init(ma_device_type_playback);
        config.playback.format = ma_format_f32;
        config.playback.channels = 2;
        config.sampleRate = stb_vorbis_get_info(decoder).sample_rate;
        config.dataCallback = render;
        config.pUserData = this;
        if (ma_device_init(nullptr, &config, &device) != MA_SUCCESS) { stop(); return false; }
        open = true;
        ended.store(false);
        return true;
    }
};
Deck decks[2];
}
extern "C" {
JNIEXPORT jboolean JNICALL Java_com_rm_infill_audio_MusicOut_nativeLoad(JNIEnv* env, jobject, jint deck, jbyteArray bytes) {
    return decks[deck].load(env, bytes);
}
JNIEXPORT void JNICALL Java_com_rm_infill_audio_MusicOut_nativePlay(JNIEnv*, jobject, jint deck, jboolean playing) {
    if (!decks[deck].open) return;
    if (playing) ma_device_start(&decks[deck].device); else ma_device_stop(&decks[deck].device);
}
JNIEXPORT void JNICALL Java_com_rm_infill_audio_MusicOut_nativeVolume(JNIEnv*, jobject, jint deck, jfloat volume) {
    decks[deck].volume.store(volume);
}
JNIEXPORT void JNICALL Java_com_rm_infill_audio_MusicOut_nativeStop(JNIEnv*, jobject, jint deck) { decks[deck].stop(); }
JNIEXPORT jboolean JNICALL Java_com_rm_infill_audio_MusicOut_nativeEnded(JNIEnv*, jobject, jint deck) { return decks[deck].ended.load(); }
}
