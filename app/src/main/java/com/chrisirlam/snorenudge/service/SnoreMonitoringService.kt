package com.chrisirlam.snorenudge.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chrisirlam.snorenudge.MainActivity
import com.chrisirlam.snorenudge.R
import com.chrisirlam.snorenudge.audio.*
import com.chrisirlam.snorenudge.data.*
import com.chrisirlam.snorenudge.watch.WatchCommandSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private const val TAG = "SnoreMonitoringService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "snore_monitor"

/**
 * Long-running foreground service that owns the audio pipeline and manages
 * the entire snore-detection lifecycle.
 *
 * Start via [startMonitoring] / stop via [stopMonitoring] convenience methods.
 */
class SnoreMonitoringService : Service() {

    companion object {
        const val ACTION_START = "com.chrisirlam.snorenudge.START_MONITORING"
        const val ACTION_STOP = "com.chrisirlam.snorenudge.STOP_MONITORING"
        const val ACTION_FAKE_SNORE = "com.chrisirlam.snorenudge.FAKE_SNORE"

        private var _isRunning = false
        val isRunning: Boolean get() = _isRunning

        fun startMonitoring(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java)
                .setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var snoreEventDao: SnoreEventDao
    private lateinit var watchCommandSender: WatchCommandSender
    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var audioFrameProcessor: AudioFrameProcessor
    private lateinit var featureExtractor: SnoreFeatureExtractor
    private lateinit var classifier: RuleBasedSnoreClassifier
    private lateinit var decisionEngine: TriggerDecisionEngine

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(applicationContext)
        val db = SnoreDatabase.getInstance(applicationContext)
        snoreEventDao = db.snoreEventDao()
        watchCommandSender = WatchCommandSender(applicationContext)
        audioFrameProcessor = AudioFrameProcessor()
        featureExtractor = SnoreFeatureExtractor()
        classifier = RuleBasedSnoreClassifier()
        decisionEngine = TriggerDecisionEngine()

        audioCaptureManager = AudioCaptureManager { rawFrame ->
            serviceScope.launch { processFrame(rawFrame) }
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoringInternal()
            ACTION_STOP -> stopSelf()
            ACTION_FAKE_SNORE -> serviceScope.launch { fireTrigger(1.0f, 0.9f, "test") }
        }
        return START_STICKY
    }

    private fun startMonitoringInternal() {
        if (_isRunning) return
        _isRunning = true

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring for snoring…"))

        decisionEngine.reset()
        audioCaptureManager.start(serviceScope)
        Log.i(TAG, "Snore monitoring started")
    }

    private suspend fun processFrame(rawFrame: ShortArray) {
        val settings = settingsDataStore.settingsFlow.first()
        val frame = audioFrameProcessor.process(rawFrame)
        val features = featureExtractor.extract(frame)
        val score = classifier.classify(features, settings.sensitivity)
        val result = decisionEngine.onFrameScore(score, settings)

        if (settings.debugMode) {
            Log.v(TAG, "rms=${frame.rms} score=$score state=${result.state} conf=${result.rollingConfidence}")
        }

        if (result.shouldTrigger) {
            fireTrigger(score, result.rollingConfidence, "snore")
        }
    }

    private suspend fun fireTrigger(score: Float, confidence: Float, source: String) {
        val settings = settingsDataStore.settingsFlow.first()
        Log.i(TAG, "Trigger fired! source=$source confidence=$confidence")

        var watchSent = false
        if (settings.watchVibrationEnabled) {
            val sent = watchCommandSender.sendVibrateCommand(strong = settings.vibrationStrong)
            watchSent = sent > 0
            Log.d(TAG, "Watch command sent to $sent nodes")
        }

        if (settings.phoneVibrationEnabled) vibratePhone(settings.vibrationStrong)
        if (settings.phoneSoundEnabled) playAlertTone()

        // Persist event (lightweight — no audio stored)
        val event = SnoreEvent(
            timestampMs = System.currentTimeMillis(),
            confidence = confidence,
            rmsLevel = 0f,
            watchCommandSent = watchSent,
            phoneVibrated = settings.phoneVibrationEnabled,
            triggerSource = source
        )
        snoreEventDao.insertEvent(event)

        // Update notification
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification("Snore detected! Nudge sent."))
    }

    private fun vibratePhone(strong: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (strong) {
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 300, 100, 300, 100, 500),
                intArrayOf(0, 255, 0, 255, 0, 255),
                -1
            ))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun playAlertTone() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator failed: ${e.message}")
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:SnoreWakeLock").apply {
            acquire(12 * 60 * 60 * 1000L) // up to 12 hours
        }
    }

    override fun onDestroy() {
        _isRunning = false
        audioCaptureManager.stop()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.i(TAG, "Snore monitoring stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Snore Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active during overnight snore monitoring"
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_snore_notification)
            .setContentTitle("SnoreNudge")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }
}
