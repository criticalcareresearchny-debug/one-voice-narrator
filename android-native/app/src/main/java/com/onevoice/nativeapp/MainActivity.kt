package com.onevoice.nativeapp

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import java.io.File

class MainActivity : Activity() {
    private lateinit var script: EditText
    private lateinit var name: EditText
    private lateinit var narrator: Spinner
    private lateinit var speed: SeekBar
    private lateinit var speedLabel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var generateButton: Button
    private lateinit var stopButton: Button
    private lateinit var openButton: Button
    private lateinit var shareButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val ink = Color.rgb(236, 246, 255)
    private val muted = Color.rgb(178, 198, 215)
    private val backdrop = Color.rgb(16, 24, 39)
    private val mint = Color.rgb(120, 227, 202)
    private val card = Color.rgb(29, 43, 65)
    private var lastUri: Uri? = null
    private var apiCallback: PendingIntent? = null
    private var apiRequestId: String? = null
    private lateinit var apiStatus: TextView

    private val poll = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backdrop
        window.navigationBarColor = backdrop

        val outer = ScrollView(this).apply { setBackgroundColor(backdrop); isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = dp(18)
            setPadding(inset, dp(24), inset, dp(44))
        }
        outer.addView(content)

        content.addView(text("♫  One Voice", 29f, mint, true))
        content.addView(text("NATIVE NARRATION STUDIO", 12f, mint))
        content.addView(text("Six natural English voices · local MP3 · background processing", 14f, muted))
        apiStatus = text("API Connect: Ready — share text from other apps or send a structured Android request.", 13f, mint)
        content.addView(apiStatus, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        content.addView(text("Your video script", 16f, ink, true).also { spacing(it, 22, 5) })
        script = EditText(this).apply {
            setTextColor(ink)
            setHintTextColor(muted)
            setBackgroundColor(card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            hint = "Paste your narration here. There is no fixed character limit."
            setTextSize(16f)
            minLines = 9
            maxLines = 20
            gravity = Gravity.TOP
            setSingleLine(false)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(File(filesDir, "onevoice-script.txt").takeIf { it.isFile }?.readText() ?: "")
        }
        content.addView(script, LinearLayout.LayoutParams(-1, -2))

        content.addView(text("Choose your narrator", 16f, ink, true).also { spacing(it, 20, 6) })
        narrator = Spinner(this)
        val options = VoiceCatalog.voices.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        narrator.adapter = adapter
        val settings = getSharedPreferences("settings", MODE_PRIVATE)
        narrator.setSelection(settings.getInt("voice", 0).coerceIn(options.indices))
        content.addView(narrator)

        content.addView(text("Narration speed", 16f, ink, true).also { spacing(it, 18, 4) })
        speedLabel = text("1.00× — Normal", 15f, mint, true)
        content.addView(speedLabel)
        speed = SeekBar(this).apply {
            max = 16
            progress = settings.getInt("speedStep", 6).coerceIn(0, 16)
        }
        content.addView(speed)
        speedLabel.text = speedText()
        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                speedLabel.text = speedText()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        content.addView(text("Slower 0.70×                                        Faster 1.50×", 12f, muted))

        content.addView(text("Recording filename", 16f, ink, true).also { spacing(it, 16, 4) })
        name = EditText(this).apply {
            setTextColor(ink)
            setHintTextColor(muted)
            setBackgroundColor(card)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setSingleLine(true)
            setText(settings.getString("filename", "my-narration"))
            hint = "my-narration"
        }
        content.addView(name, LinearLayout.LayoutParams(-1, -2))

        generateButton = button("♫  Generate MP3 in background", true).also {
            spacing(it, 18, 0)
            content.addView(it)
        }
        stopButton = button("Stop generation", false).also {
            spacing(it, 8, 0)
            content.addView(it)
        }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(16) })
        status = text("Ready for your script", 14f, muted)
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        openButton = button("▶ Play finished MP3", false).also {
            spacing(it, 17, 0)
            content.addView(it)
        }
        shareButton = button("Share / import MP3 into video editor", false).also {
            spacing(it, 7, 0)
            content.addView(it)
        }

        content.addView(text(
            "While the notification says 'Generating', you may use other apps. " +
            "Android may still interrupt processing under low-memory or battery restrictions. " +
            "On first launch the voice model is copied from the APK into private storage; " +
            "this can take a little time. Finished audio is in Downloads / One Voice.",
            12f, muted
        ).also { spacing(it, 20, 0) })
        setContentView(outer)
        importExternalRequest(intent)

        generateButton.setOnClickListener { begin() }
        stopButton.setOnClickListener {
            startService(Intent(this, NarrationService::class.java).setAction(NarrationService.STOP))
        }
        openButton.setOnClickListener {
            val uri = lastUri ?: return@setOnClickListener
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "audio/mpeg")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                })
            } catch (err: Exception) {
                toast("No audio player found; use the Downloads app.")
            }
        }
        shareButton.setOnClickListener {
            val uri = lastUri ?: return@setOnClickListener
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Send narration to"))
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    override fun onNewIntent(next: Intent) {
        super.onNewIntent(next)
        setIntent(next)
        importExternalRequest(next)
    }

    private fun importExternalRequest(incoming: Intent?) {
        if (incoming == null) return
        val isShare = incoming.action == Intent.ACTION_SEND && incoming.type == "text/plain"
        val isApi = incoming.action == ApiContract.ACTION_GENERATE
        if (!isShare && !isApi) return
        if (getSharedPreferences("state", MODE_PRIVATE).getString("jobState", "") == "running") {
            toast("Finish the current recording before sending another script.")
            return
        }
        val incomingText = if (isShare) {
            incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        } else {
            incoming.getStringExtra(ApiContract.EXTRA_TEXT)
                ?: incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        }?.trim().orEmpty()
        if (incomingText.isEmpty()) {
            toast("The other app did not include any narration text.")
            return
        }
        if (incomingText.length > ApiContract.MAX_EXTERNAL_CHARS) {
            toast("External requests are limited to 50,000 characters. Split the script.")
            return
        }
        script.setText(incomingText)
        script.setSelection(0)
        apiCallback = null
        apiRequestId = null
        if (isApi) {
            val voiceCode = incoming.getStringExtra(ApiContract.EXTRA_VOICE)
            val index = VoiceCatalog.voices.indexOfFirst { it.code == voiceCode }
            if (index >= 0) narrator.setSelection(index)
            val requestedSpeed = incoming.getFloatExtra(ApiContract.EXTRA_SPEED, 1.0f)
            if (requestedSpeed.isFinite() && requestedSpeed >= 0.7f && requestedSpeed <= 1.5f) {
                speed.progress = ((requestedSpeed - 0.7f) / 0.05f).toInt().coerceIn(0, 16)
                speedLabel.text = speedText()
            }
            incoming.getStringExtra(ApiContract.EXTRA_FILENAME)?.take(70)?.let {
                if (it.isNotBlank()) name.setText(it)
            }
            apiRequestId = incoming.getStringExtra(ApiContract.EXTRA_REQUEST_ID)?.take(100)
            val suppliedCallback = ApiContract.readCallback(incoming)
            if (suppliedCallback != null && suppliedCallback.isImmutable) {
                toast("Callback must be a mutable, explicit PendingIntent.")
            } else {
                apiCallback = suppliedCallback
            }
            apiStatus.text = "API Connect: Request received. Review and tap Generate to approve." +
                (if (apiCallback != null) " Finished MP3 will be returned to the calling app." else "")
        } else {
            apiStatus.text = "API Connect: Shared text imported. Tap Generate to make an MP3."
        }
        toast("Script imported from another app. Tap Generate to approve.")
    }

    private fun begin() {
        val story = script.text.toString().trim()
        if (story.isEmpty()) {toast("Paste a script first."); return}
        File(filesDir, "onevoice-script.txt").writeText(story)
        val filename = name.text.toString().trim().ifBlank { "my-narration" }
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putInt("voice", narrator.selectedItemPosition)
            .putInt("speedStep", speed.progress)
            .putString("filename", filename)
            .apply()
        getSharedPreferences("state", MODE_PRIVATE).edit()
            .putString("jobState", "running").putString("message", "Preparing narration")
            .putInt("progress", 0).apply()
        try {
            val intent = Intent(this, NarrationService::class.java).apply {
                action = NarrationService.START
                putExtra(NarrationService.EXTRA_VOICE, narrator.selectedItemPosition)
                putExtra(NarrationService.EXTRA_SPEED, speedFactor())
                putExtra(NarrationService.EXTRA_NAME, filename)
                apiCallback?.let { putExtra(ApiContract.EXTRA_CALLBACK, it) }
                apiRequestId?.let { putExtra(ApiContract.EXTRA_REQUEST_ID, it) }
            }
            startForegroundService(intent)
            apiCallback = null
            apiRequestId = null
            apiStatus.text = "API Connect: Recording submitted. You may use other apps."
        } catch (err: Exception) {
            getSharedPreferences("state", MODE_PRIVATE).edit()
                .putString("jobState", "error").putString("message", "Could not start narration").apply()
            ApiContract.sendResult(this, apiCallback, apiRequestId,
                ApiContract.STATUS_ERROR, message = err.message ?: "Unable to start")
            apiCallback = null
            apiRequestId = null
            toast("Could not start background narration: " + (err.message ?: "unknown error"))
        }
        updateStatus()
    }

    private fun updateStatus() {
        val p = getSharedPreferences("state", MODE_PRIVATE)
        val state = p.getString("jobState", "idle") ?: "idle"
        status.text = p.getString("message", "Ready for your script")
        val percent = p.getInt("progress", 0)
        progress.isIndeterminate = state == "running" && percent < 0
        if (percent >= 0) progress.progress = percent
        val running = state == "running"
        generateButton.isEnabled = !running
        stopButton.isEnabled = running
        val result = p.getString("lastUri", null)
        lastUri = result?.let { Uri.parse(it) }
        openButton.isEnabled = lastUri != null
        shareButton.isEnabled = lastUri != null
    }

    private fun speedFactor(): Float = 0.70f + speed.progress * 0.05f
    private fun speedText(): String = String.format(
        java.util.Locale.US, "%.2f× — %s", speedFactor(),
        if (speed.progress == 6) "Normal" else if (speed.progress < 6) "Slower" else "Faster"
    )

    private fun button(value: String, primary: Boolean) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(if (primary) backdrop else ink)
        setBackgroundColor(if (primary) mint else card)
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            setTextColor(color)
            textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    private fun spacing(view: View, top: Int, bottom: Int) {
        view.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }
    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }
}
