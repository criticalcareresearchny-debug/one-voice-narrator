package com.onevoice.nativeapp

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ModelStore {
    fun prepare(context: Context, status: (String) -> Unit): File {
        val folder = File(context.filesDir, "kokoro-int8-v1").apply { mkdirs() }
        val ready = File(folder, ".ready")
        val model = File(folder, "model.int8.onnx")
        val voices = File(folder, "voices.bin")
        if (ready.exists() && model.length() > 60_000_000 && voices.length() > 1_000_000) return folder
        status("Preparing the offline voice model (one-time setup)")
        copyAssets(context, "kokoro", folder)
        if (!model.exists() || model.length() < 60_000_000 || !voices.exists() || voices.length() < 1_000_000) {
            throw IllegalStateException("The APK is missing its complete Kokoro model.")
        }
        if (!File(folder, "tokens.txt").exists()) throw IllegalStateException("Missing Kokoro tokens.")
        ready.writeText("1")
        return folder
    }
    private fun copyAssets(context: Context, assetPath: String, output: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            output.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(output).use { sink -> input.copyTo(sink, 65536) }
            }
        } else {
            output.mkdirs()
            for (name in children) {
                copyAssets(context, assetPath + "/" + name, File(output, name))
            }
        }
    }
}
