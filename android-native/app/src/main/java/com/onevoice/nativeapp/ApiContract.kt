package com.onevoice.nativeapp

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * Local Android app-to-app narration API. CPU-intensive generation requires
 * an explicit user tap in the visible activity; no public HTTP listener.
 */
object ApiContract {
    const val ACTION_GENERATE = "com.onevoice.nativeapp.action.GENERATE_NARRATION"
    const val ACTION_RESULT = "com.onevoice.nativeapp.action.NARRATION_RESULT"
    const val EXTRA_TEXT = "com.onevoice.nativeapp.extra.TEXT"
    const val EXTRA_VOICE = "com.onevoice.nativeapp.extra.VOICE"
    const val EXTRA_SPEED = "com.onevoice.nativeapp.extra.SPEED"
    const val EXTRA_FILENAME = "com.onevoice.nativeapp.extra.FILENAME"
    const val EXTRA_CALLBACK = "com.onevoice.nativeapp.extra.CALLBACK"
    const val EXTRA_REQUEST_ID = "com.onevoice.nativeapp.extra.REQUEST_ID"
    const val EXTRA_STATUS = "com.onevoice.nativeapp.extra.STATUS"
    const val EXTRA_RESULT_URI = "com.onevoice.nativeapp.extra.RESULT_URI"
    const val EXTRA_MESSAGE = "com.onevoice.nativeapp.extra.MESSAGE"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_ERROR = "error"
    const val STATUS_CANCELLED = "cancelled"
    const val STATUS_BUSY = "busy"
    const val MAX_EXTERNAL_CHARS = 50_000

    @Suppress("DEPRECATION")
    fun readCallback(intent: Intent): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_CALLBACK, PendingIntent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_CALLBACK)
        }

    fun sendResult(
        context: Context,
        callback: PendingIntent?,
        requestId: String?,
        status: String,
        uri: Uri? = null,
        message: String? = null
    ) {
        if (callback == null) return
        val result = Intent(ACTION_RESULT).apply {
            putExtra(EXTRA_REQUEST_ID, requestId.orEmpty())
            putExtra(EXTRA_STATUS, status)
            if (message != null) putExtra(EXTRA_MESSAGE, message.take(500))
            if (uri != null) {
                data = uri
                clipData = ClipData.newUri(context.contentResolver, "One Voice MP3", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(EXTRA_RESULT_URI, uri.toString())
            }
        }
        if (uri != null) {
            try {
                val receiverPackage = callback.creatorPackage
                if (!receiverPackage.isNullOrEmpty()) {
                    context.grantUriPermission(receiverPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (exception: Exception) {
                Log.w("OneVoiceAPI", "Could not grant MP3 read access", exception)
            }
        }
        try {
            callback.send(
                context,
                if (status == STATUS_COMPLETED) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                result
            )
        } catch (exception: PendingIntent.CanceledException) {
            Log.w("OneVoiceAPI", "Caller cancelled the result callback")
        }
    }
}
