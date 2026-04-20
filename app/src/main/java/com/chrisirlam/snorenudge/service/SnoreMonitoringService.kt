package com.chrisirlam.snorenudge.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.chrisirlam.snorenudge.MainActivity
import com.chrisirlam.snorenudge.R
import com.chrisirlam.snorenudge.audio.*
import com.chrisirlam.snorenudge.data.*
import com.chrisirlam.snorenudge.watch.WatchCommandSender
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SnoreMonitoringService"
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "snore_monitor"
private const val PREFS_NAME = "snore_service_prefs"
private const val PREF_WAS_MONITORING = "was_monitoring"

/**
 * Long-running foreground service that owns the audio pipeline and manages
 * the entire snore-detection lifecycle.
 *
 * Start via [startMonitoring] / stop via [stopMonitoring] convenience methods.
 *
 * Samsung robustness:
 * - Uses PARTIAL_WAKE_LOCK to keep the CPU alive.
 * - Persists a "was_monitoring" flag in SharedPreferences so [BootReceiver] can
 *   restart the service after a reboot or OS-forced kill.
 * - The foreground notification uses IMPORTANCE_DEFAULT on Samsung to resist
 *   aggressive battery management.
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
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun sendFakeTrigger(context: Context) {
            val intent = Intent(context, SnoreMonitoringService::class.java)
                .setAction(ACTION_FAKE_SNORE)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Write the monitoring-active flag synchronously (safe from any thread/receiver). */
        fun persistMonitoringFlag(context: Context, active: Boolean) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_WAS_MONITORING, active)
                .apply()
        }

        /** Read the monitoring-active flag synchronously (safe from BootReceiver). */
        fun wasMonitoring(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_WAS_MONITORING, false)
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

    private var settingsJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastTriggerMs: Long? = null
    private var currentSettings = AppSettings()
    private val triggerMutex = Mutex()
    private var explicitStopRequested = false

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

        settingsJob = serviceScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                currentSettings = settings
            }
        }

        audioCaptureManager = AudioCaptureManager(::processFrame)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoringInternal()
            ACTION_STOP -> {
                explicitStopRequested = true
                stopSelf()
            }
            ACTION_FAKE_SNORE -> handleFakeTrigger(startId)
        }
        return START_STICKY
    }

    private fun startMonitoringInternal() {
        explicitStopRequested = false
        if (_isRunning) {
            startForeground(NOTIFICATION_ID, buildNotification("Monitoring for snoring…"))
            return
        }
        _isRunning = true

        acquireWakeLock()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring for snoring…"))
        persistMonitoringFlag(applicationContext, true)

        decisionEngine.reset()
        ServiceBridge.reset()
        audioCaptureManager.start(serviceScope)
        Log.i(TAG, "Snore monitoring started")
    }

    private fun handleFakeTrigger(startId: Int) {
        val temporaryForeground = !_isRunning
        if (temporaryForeground) {
            startForeground(NOTIFICATION_ID, buildNotification("Sending test nudge…"))
        }

        serviceScope.launch {
            fireTrigger(1.0f, 1.0f, 0f, 0f, 0f, "test")
            if (temporaryForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
    }

    private fun processFrame(rawFrame: ShortArray) {
        val settings = currentSettings
        val frame = audioFrameProcessor.process(rawFrame)
        val features = featureExtractor.extract(frame)
        val score = classifier.classify(features, settings.sensitivity)
        val result = decisionEngine.onFrameScore(score, settings)

        ServiceBridge.update(
            ServiceBridge.LiveState(
                frameConfidence = result.frameConfidence,
                rollingConfidence = result.rollingConfidence,
                triggerThreshold = result.triggerThreshold,
                engineState = result.state,
                cooldownRemainingMs = result.cooldownRemainingMs,
                lastTriggerMs = lastTriggerMs,
                rmsLevel = frame.rms,
                zeroCrossingRate = features.zeroCrossingRate,
                lowFrequencyRatio = features.lowFrequencyRatio
            )
        )

        if (settings.debugMode) {
            Log.v(
                TAG,
                "rms=${frame.rms} zcr=${features.zeroCrossingRate} lowFreq=${features.lowFrequencyRatio} " +
                        "frame=$score rolling=${result.rollingConfidence} state=${result.state}"
            )
        }

        if (result.shouldTrigger) {
            serviceScope.launch {
                fireTrigger(
                    score = score,
                    confidence = result.rollingConfidence,
                    rmsLevel = frame.rms,
                    zeroCrossingRate = features.zeroCrossingRate,
                    lowFrequencyRatio = features.lowFrequencyRatio,
                    source = "snore"
                )
            }
        }
    }

    private suspend fun fireTrigger(
        score: Float,
        confidence: Float,
        rmsLevel: Float,
        zeroCrossingRate: Float,
        lowFrequencyRatio: Float,
        source: String
    ) {
        triggerMutex.withLock {
            val settings = currentSettings
            val nowMs = System.currentTimeMillis()
            val effectiveCooldownSeconds = settings.cooldownDurationSeconds.coerceAtLeast(20)
            val cooldownMs = effectiveCooldownSeconds * 1000L
            val previousTriggerMs = lastTriggerMs
            if (source != "test" && previousTriggerMs != null && nowMs - previousTriggerMs < cooldownMs) {
                Log.d(TAG, "Trigger suppressed by cooldown: source=$source")
                return
            }

            lastTriggerMs = nowMs
            Log.i(
                TAG,
                "Trigger fired! source=$source score=$score confidence=$confidence rms=$rmsLevel " +
                    "zcr=$zeroCrossingRate lowFreq=$lowFrequencyRatio"
            )

            var watchSent = false
            if (settings.watchVibrationEnabled) {
                val sent = watchCommandSender.sendVibrateCommand(strong = settings.vibrationStrong)
                watchSent = sent > 0
                Log.d(TAG, "Watch command sent to $sent nodes")
            }

            if (settings.phoneVibrationEnabled) vibratePhone(settings.vibrationStrong)
            if (settings.phoneSoundEnabled) playAlertTone()

            val event = SnoreEvent(
                timestampMs = nowMs,
                confidence = confidence,
                rmsLevel = rmsLevel,
                watchCommandSent = watchSent,
                phoneVibrated = settings.phoneVibrationEnabled,
                triggerSource = source
            )
            snoreEventDao.insertEvent(event)

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, buildNotification("Snore detected! Nudge sent."))
        }
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
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:SnoreWakeLock").apply {
            acquire(12 * 60 * 60 * 1000L) // up to 12 hours
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (_isRunning && wasMonitoring(applicationContext) && !explicitStopRequested) {
            scheduleSelfRestart()
        }
    }

    override fun onDestroy() {
        val shouldRestart = _isRunning && !explicitStopRequested && wasMonitoring(applicationContext)
        _isRunning = false
        audioCaptureManager.stop()
        settingsJob?.cancel()
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceBridge.reset()
        if (explicitStopRequested) {
            persistMonitoringFlag(applicationContext, false)
        } else if (shouldRestart) {
            scheduleSelfRestart()
        }
        Log.i(TAG, "Snore monitoring stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        // Use IMPORTANCE_DEFAULT on Samsung to reduce the chance of the service
        // being killed by aggressive battery management (One UI Device Care).
        val importance = if (isSamsungDevice()) {
            NotificationManager.IMPORTANCE_DEFAULT
        } else {
            NotificationManager.IMPORTANCE_LOW
        }
        val channel = NotificationChannel(CHANNEL_ID, "Snore Monitor", importance).apply {
            description = "Active during overnight snore monitoring"
            setShowBadge(false)
            // Prevent audible alerts from the service notification itself
            setSound(null, null)
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

    private fun scheduleSelfRestart() {
        val restartIntent = Intent(this, SnoreMonitoringService::class.java).setAction(ACTION_START)
        val pendingIntent = PendingIntent.getService(
            this,
            1,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 5_000L,
            pendingIntent
        )
        Log.i(TAG, "Scheduled monitoring restart")
    }

    private fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}
