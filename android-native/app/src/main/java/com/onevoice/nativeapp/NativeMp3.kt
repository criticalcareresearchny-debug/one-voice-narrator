package com.onevoice.nativeapp

import java.io.Closeable
import java.io.File
import java.io.IOException

class NativeMp3(file: File, sampleRate: Int) : Closeable {
    private var ptr = openNative(file.absolutePath, sampleRate, 96)
    init {
        if (ptr == 0L) throw IOException("Could not initialize the MP3 encoder.")
    }
    fun append(samples: FloatArray) {
        val shorts = ShortArray(samples.size)
        for (i in samples.indices) {
            val value = samples[i].coerceIn(-1.0f, 1.0f)
            shorts[i] = (value * (if (value < 0f) 32768f else 32767f)).toInt().toShort()
        }
        if (!writeNative(ptr, shorts)) throw IOException("The MP3 encoder could not write audio.")
    }
    fun silence(milliseconds: Int, sampleRate: Int) {
        val frames = (sampleRate.toLong() * milliseconds / 1000L).toInt()
        if (frames > 0 && !writeNative(ptr, ShortArray(frames))) throw IOException("Could not encode pause.")
    }
    override fun close() {
        if (ptr != 0L) {
            val ok = closeNative(ptr)
            ptr = 0
            if (!ok) throw IOException("The MP3 encoder could not finish the file.")
        }
    }
    private external fun openNative(path: String, sampleRate: Int, bitrate: Int): Long
    private external fun writeNative(pointer: Long, samples: ShortArray): Boolean
    private external fun closeNative(pointer: Long): Boolean
    companion object {
        init { System.loadLibrary("onevoice-mp3") }
    }
}
