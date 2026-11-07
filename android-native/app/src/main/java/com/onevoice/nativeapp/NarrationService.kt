package com.onevoice.nativeapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Android foreground service does speech inference on its own executor,
 * not in an Activity/WebView. It remains active when another app is opened.
 * Android battery policy, force-stop and media-processing limits still apply.
 */
class NarrationService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val cancel = AtomicBoolean(false)
    private var active = false
    private val channelId = "onevoice_narration"
    private val notificationId = 1001

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(channelId, "Narration processing", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            cancel.set(true)
            status("Stopping after current section", -1)
            return START_NOT_STICKY
        }
        if (intent?.action != START || active) return START_NOT_STICKY
        active = true
        cancel.set(false)
        val initial = notification("Preparing narration", -1)
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(notificationId, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else {
            startForeground(notificationId, initial)
        }
        worker.execute {
            try {
                generate(intent)
            } catch (err: Exception) {
                status("Error: " + (err.message ?: err.javaClass.simpleName), -1, "error")
            } catch (err: UnsatisfiedLinkError) {
                status("Native voice library missing: " + (err.message ?: "unknown"), -1, "error")
            } finally {
                active = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun generate(intent: Intent) {
        val script = File(filesDir, "onevoice-script.txt").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        if (script.isEmpty()) throw IllegalArgumentException("Your narration script is empty.")
        val voiceIndex = intent.getIntExtra(EXTRA_VOICE, 0).coerceIn(0, VoiceCatalog.voices.lastIndex)
        val voice = VoiceCatalog.voices[voiceIndex]
        val speed = intent.getFloatExtra(EXTRA_SPEED, 1f)
        if (!speed.isFinite() || speed < 0.7f || speed > 1.5f) throw IllegalArgumentException("Invalid speed.")
        val name = (intent.getStringExtra(EXTRA_NAME) ?: "my-narration")
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-', '_', '.').take(70).ifBlank { "my-narration" }
        val sections = ScriptSplitter.sections(script)
        if (sections.isEmpty()) throw IllegalArgumentException("No text to narrate.")
        status("Preparing voice files", 0)
        val dir = ModelStore.prepare(this) { status(it, 0) }
        if (cancel.get()) {status("Cancelled", -1, "cancelled"); return}
        val modelFile = File(dir, "model.int8.onnx")
        val voicesFile = File(dir, "voices.bin")
        val tokensFile = File(dir, "tokens.txt")
        val lexiconUs = File(dir, "lexicon-us-en.txt")
        val lexiconGb = File(dir, "lexicon-gb-en.txt")
        val modelConfig = OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(
                model = modelFile.absolutePath,
                voices = voicesFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = File(dir, "espeak-ng-data").absolutePath,
                lexicon = listOf(lexiconUs, lexiconGb).filter { it.isFile }
                    .joinToString(",") { it.absolutePath }
            ),
            numThreads = 3,
            debug = false,
            provider = "cpu"
        )
        status("Loading speech engine", 0)
        var tts: OfflineTts? = null
        val tmp = File(cacheDir, "onevoice-" + System.currentTimeMillis() + ".mp3")
        try {
            tts = OfflineTts(config = OfflineTtsConfig(model = modelConfig))
            val sampleRate = tts.sampleRate()
            if (sampleRate <= 0) throw IllegalStateException("Invalid voice sampling rate.")
            NativeMp3(tmp, sampleRate).use { mp3 ->
                for (i in sections.indices) {
                    if (cancel.get()) break
                    val section = sections[i]
                    status("Generating " + (i + 1) + " of " + sections.size + " sections", (i * 100 / sections.size))
                    val audio = tts.generate(section.first, sid = voice.speakerId, speed = speed)
                    if (audio.sampleRate != sampleRate || audio.samples.isEmpty()) {
                        throw IllegalStateException("The speech engine produced invalid audio.")
                    }
                    mp3.append(audio.samples)
                    if (section.second > 0) mp3.silence(section.second, sampleRate)
                    status("Generated " + (i + 1) + " of " + sections.size + " sections", ((i + 1) * 100 / sections.size))
                }
            }
            if (cancel.get()) {
                status("Cancelled; no recording saved", -1, "cancelled")
                return
            }
            if (!tmp.exists() || tmp.length() < 1000L) throw IllegalStateException("The recording was empty.")
            status("Saving MP3 to Downloads", 99)
            val uri = publishRecording(tmp, name + ".mp3")
            getSharedPreferences("state", MODE_PRIVATE).edit()
                .putString("lastUri", uri.toString())
                .putString("lastName", name + ".mp3")
                .apply()
            status("MP3 ready — " + name + ".mp3", 100, "completed")
        } finally {
            tts?.release()
            tmp.delete()
        }
    }

    private fun publishRecording(file: File, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/One Voice")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the MP3 download.")
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output, 65536) }
            } ?: throw IllegalStateException("Downloads storage is not writable.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            return uri
        } catch (err: Exception) {
            contentResolver.delete(uri, null, null)
            throw err
        }
    }

    private fun status(label: String, progress: Int, state: String = "running") {
        getSharedPreferences("state", MODE_PRIVATE).edit()
            .putString("message", label).putInt("progress", progress).putString("jobState", state).apply()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            notificationId, notification(label, progress)
        )
    }

    private fun notification(text: String, progress: Int): Notification {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("One Voice")
            .setContentText(text)
            .setContentIntent(intent)
            .setOngoing(active)
            .setOnlyAlertOnce(true)
        if (progress in 0..99) builder.setProgress(100, progress, false)
        if (active) {
            val stopIntent = PendingIntent.getService(
                this, 1, Intent(this, NarrationService::class.java).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent
            ).build())
        }
        return builder.build()
    }

    override fun onDestroy() {
        cancel.set(true)
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        const val START = "com.onevoice.nativeapp.START"
        const val STOP = "com.onevoice.nativeapp.STOP"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_NAME = "name"
    }
}
