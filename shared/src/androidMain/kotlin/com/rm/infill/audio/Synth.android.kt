package com.rm.infill.audio

/** The synth over JNI, from the app's native library (app/src/main/cpp). */
internal actual object Synth {
    actual fun load(): Boolean {
        System.loadLibrary("infill_audio")
        return true
    }

    actual external fun nativeActiveVoices(): Int
    actual external fun nativeStart(voiceBudget: Int): Boolean
    actual external fun nativeStop()
    actual external fun nativePause(paused: Boolean)
    actual external fun nativeScene(count: Int, keys: IntArray, recipes: IntArray, flags: IntArray, params: FloatArray)
    actual external fun nativeEvent(recipe: Int, flags: Int, seed: Int, delay: Float, params: FloatArray)
    actual external fun nativeBusGains(gains: FloatArray)
    actual external fun nativeRoom(amount: Float)
}
