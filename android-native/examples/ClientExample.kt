// Example app-to-app integration for another Android program.
// Replace the client package/receiver with your real app's class.
package com.example.videoapp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

fun submitOneVoiceNarration(context: Context, text: String) {
    val callbackIntent = Intent().setClassName(
        context.packageName, context.packageName + ".NarrationResultReceiver"
    )
    val callback = PendingIntent.getBroadcast(
        context, 42, callbackIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    val request = Intent("com.onevoice.nativeapp.action.GENERATE_NARRATION").apply {
        setClassName("com.onevoice.nativeapp", "com.onevoice.nativeapp.MainActivity")
        putExtra("com.onevoice.nativeapp.extra.TEXT", text)
        putExtra("com.onevoice.nativeapp.extra.VOICE", "bm_george")
        putExtra("com.onevoice.nativeapp.extra.SPEED", 0.90f)
        putExtra("com.onevoice.nativeapp.extra.FILENAME", "video-narration")
        putExtra("com.onevoice.nativeapp.extra.REQUEST_ID", "video-001")
        putExtra("com.onevoice.nativeapp.extra.CALLBACK", callback)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(request)
}

// Register as a receiver in YOUR OWN app's AndroidManifest.xml.
class NarrationResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getStringExtra("com.onevoice.nativeapp.extra.STATUS")
        if (status == "completed") {
            val mp3: Uri = intent.data ?: return
            context.contentResolver.openInputStream(mp3)?.use { input ->
                // Import bytes into your own video editor or project storage.
                // Do not keep this input stream open after the callback returns.
            }
        } else {
            val reason = intent.getStringExtra("com.onevoice.nativeapp.extra.MESSAGE")
            // status is error, cancelled, or busy.
        }
    }
}
