package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.data.local.PreferencesManager
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object AppForegroundState {
    @Volatile
    var isInForeground: Boolean = false
}

enum class ReconnectSource {
    IN_PROCESS_CALLBACK,
    WORK_MANAGER,
    PENDING_INTENT
}

data class ReminderMessageTemplate(
    val title: String,
    val buildBody: (firstName: String) -> String
)

object CompetitionReminderMessages {
    private val templates = listOf(
        ReminderMessageTemplate(
            title = "رقبا دارن جلو می‌زنن!",
            buildBody = { name ->
                val prefix = if (name.isNotBlank()) "$name عزیز، " else ""
                "${prefix}خیلی‌ها همین الان امتیاز گرفتن و به جایگاهت نزدیک شدن؛ به بازی سر بزن و رتبه‌ات رو حفظ کن!"
            }
        ),
        ReminderMessageTemplate(
            title = "فقط چند قدم تا صدر جدول",
            buildBody = { name ->
                val prefix = if (name.isNotBlank()) "$name جان، " else ""
                "${prefix}رقابت این دوره داغه! یه سر به جدول و مربع‌های امروزت بزن."
            }
        ),
        ReminderMessageTemplate(
            title = "جایزه بزرگ منتظرته!",
            buildBody = { name ->
                val prefix = if (name.isNotBlank()) "$name عزیز، " else ""
                "${prefix}تا آنلاین هستی چند مربع جدید رو باز کن و امتیازت رو بالا ببر."
            }
        ),
        ReminderMessageTemplate(
            title = "خطر افت رتبه در جدول!",
            buildBody = { _ ->
                "نفر پشت‌سرت خیلی بهت نزدیک شده؛ همین الان وارد رقابت شو تا از کورس جایزه جا نمونی."
            }
        ),
        ReminderMessageTemplate(
            title = "اینترنتت وصل شد، وقت رقابته!",
            buildBody = { name ->
                val prefix = if (name.isNotBlank()) "$name عزیز، " else ""
                "${prefix}مربع‌های امروزت هنوز منتظرن؛ چند دقیقه وقت بذار و امتیازهات رو ثبت کن."
            }
        ),
        ReminderMessageTemplate(
            title = "فرصت طلایی امتیازگیری",
            buildBody = { _ ->
                "امروز هنوز به مسابقه سر نزدی! هر مربع می‌تونه جایگاهت رو تو جدول جابه‌جا کنه."
            }
        ),
        ReminderMessageTemplate(
            title = "رقیب‌هات بیکار ننشستن!",
            buildBody = { _ ->
                "کاربرهای دیگه همین الان در حال جمع‌کردن امتیاز هستن. نوبت توئه که اختلاف رو کم کنی."
            }
        ),
        ReminderMessageTemplate(
            title = "رتبه‌ات رو تو جدول چک کن",
            buildBody = { name ->
                val prefix = if (name.isNotBlank()) "$name جان، " else ""
                "${prefix}ممکنه توی جدول رده‌بندی جابه‌جا شده باشی؛ یه سر به بازی بزن و وضعیتت رو ببین."
            }
        ),
        ReminderMessageTemplate(
            title = "یه حرکت سریع برای برد",
            buildBody = { _ ->
                "با باز کردن چند مربع دیگه می‌تونی امتیازت رو بیشتر کنی و به برنده شدن نزدیک‌تر بشی. بزن بریم!"
            }
        ),
        ReminderMessageTemplate(
            title = "شمارش معکوس دوره ادامه داره",
            buildBody = { _ ->
                "زمان این دوره در حال گذره و مربع‌های آزاد منتظرت هستن؛ فرصت امروز رو از دست نده."
            }
        ),
        ReminderMessageTemplate(
            title = "دعوت دوباره به باشگاه رقابت",
            buildBody = { name ->
                val prefix = if (name.isNotBlank()) "$name عزیز، " else ""
                "${prefix}الان بهترین وقته که به مسابقه سر بزنی و شانس جایزه‌ات رو بیشتر کنی."
            }
        ),
        ReminderMessageTemplate(
            title = "جایگاهت رو پس بگیر!",
            buildBody = { _ ->
                "رقبا امتیازهای تازه‌ای ثبت کردن؛ برگرد به بازی و دوباره توی جدول اوج بگیر."
            }
        )
    )

    fun messageCount(): Int = templates.size

    fun pickNextMessage(
        prefs: PreferencesManager,
        random: Random = Random.Default
    ): Pair<String, String> {
        val lastIdx = prefs.lastReminderMessageIndex
        val candidates = templates.indices.filter { it != lastIdx }
        val chosenIdx = candidates[random.nextInt(candidates.size)]
        prefs.lastReminderMessageIndex = chosenIdx
        val template = templates[chosenIdx]
        val firstName = prefs.firstName.trim()
        return template.title to template.buildBody(firstName)
    }
}

object CompetitionReminderManager {
    private const val TAG = "CompetitionReminder"
    const val CHANNEL_ID = "COMPETITION_REMINDER_CHANNEL"
    const val NOTIFICATION_ID = 1001
    const val UNIQUE_RECONNECT_WORK = "network_reconnect_work"
    const val UNIQUE_INACTIVITY_WORK = "reminder_work"
    const val ACTION_NETWORK_AVAILABLE = "com.example.ACTION_NETWORK_AVAILABLE"

    private const val RECONNECT_COOLDOWN_MS = 30_000L
    private const val BACKGROUND_GRACE_MS = 1_500L
    private const val REGISTRATION_GRACE_MS = 5_000L

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun canNotify(context: Context, prefs: PreferencesManager): Boolean {
        if (!prefs.isLoggedIn || prefs.userId.isBlank()) return false
        if (!prefs.notificationsEnabled) return false
        if (AppForegroundState.isInForeground) return false
        return hasNotificationPermission(context)
    }

    @Synchronized
    fun maybeNotifyOnReconnect(
        context: Context,
        source: ReconnectSource,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val appContext = context.applicationContext
        val prefs = PreferencesManager(appContext)
        if (!canNotify(appContext, prefs)) return false
        if (!isNetworkConnected(appContext)) return false

        if (nowMs - prefs.lastReminderShownAtMs < RECONNECT_COOLDOWN_MS) {
            return false
        }
        if (prefs.lastAppBackgroundAtMs > 0L && nowMs - prefs.lastAppBackgroundAtMs < BACKGROUND_GRACE_MS) {
            return false
        }
        if (source == ReconnectSource.PENDING_INTENT &&
            !prefs.wasOfflineSinceLastVisit &&
            nowMs - prefs.networkPendingIntentRegisteredAtMs < REGISTRATION_GRACE_MS
        ) {
            return false
        }

        return showVariedNotification(appContext, prefs, nowMs)
    }

    @SuppressLint("MissingPermission")
    fun showVariedNotification(
        context: Context,
        prefs: PreferencesManager,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "یادآوری رقابت و جوایز",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "اعلان‌های یادآوری برای حضور در مسابقه و حفظ رتبه"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val (title, body) = CompetitionReminderMessages.pickNextMessage(prefs)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            101,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIconBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_trophy)
        }.getOrNull()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_trophy)
            .setColor(0xFFD4AF37.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(nowMs)
            .setShowWhen(true)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
        prefs.lastReminderShownAtMs = nowMs
        prefs.wasOfflineSinceLastVisit = false
        return true
    }

    fun registerPersistentNetworkCallback(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val prefs = PreferencesManager(appContext)
        if (!prefs.isLoggedIn || !prefs.notificationsEnabled) return
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val pendingIntent = buildNetworkPendingIntent(appContext)
            prefs.networkPendingIntentRegisteredAtMs = System.currentTimeMillis()
            cm.registerNetworkCallback(request, pendingIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register persistent network callback", e)
        }
    }

    fun unregisterPersistentNetworkCallback(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            cm.unregisterNetworkCallback(buildNetworkPendingIntent(appContext))
        } catch (_: Exception) {
        }
    }

    private fun buildNetworkPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NetworkReconnectReceiver::class.java).apply {
            action = ACTION_NETWORK_AVAILABLE
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 202, intent, flags)
    }

    fun scheduleReconnectWork(context: Context) {
        val appContext = context.applicationContext
        val prefs = PreferencesManager(appContext)
        if (!prefs.isLoggedIn || !prefs.notificationsEnabled) return
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<NetworkReconnectWorker>()
                .setConstraints(constraints)
                .addTag(UNIQUE_RECONNECT_WORK)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_RECONNECT_WORK,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule reconnect work", e)
        }
    }

    fun cancelReconnectWork(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_RECONNECT_WORK)
        } catch (_: Exception) {
        }
    }

    fun scheduleInactivityReminder(context: Context) {
        val appContext = context.applicationContext
        val prefs = PreferencesManager(appContext)
        if (!prefs.isLoggedIn || !prefs.notificationsEnabled) return
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setConstraints(constraints)
                .setInitialDelay(24, TimeUnit.HOURS)
                .addTag(UNIQUE_INACTIVITY_WORK)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                UNIQUE_INACTIVITY_WORK,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule inactivity reminder", e)
        }
    }

    fun onUserAuthenticated(context: Context) {
        val appContext = context.applicationContext
        val prefs = PreferencesManager(appContext)
        prefs.lastAppForegroundAtMs = System.currentTimeMillis()
        prefs.wasOfflineSinceLastVisit = false
        registerPersistentNetworkCallback(appContext)
        scheduleInactivityReminder(appContext)
    }

    fun onUserLoggedOut(context: Context) {
        val appContext = context.applicationContext
        cancelReconnectWork(appContext)
        try {
            WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_INACTIVITY_WORK)
        } catch (_: Exception) {
        }
        unregisterPersistentNetworkCallback(appContext)
    }
}

class NetworkReconnectWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        CompetitionReminderManager.maybeNotifyOnReconnect(
            context = applicationContext,
            source = ReconnectSource.WORK_MANAGER
        )
        return Result.success()
    }
}

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        if (CompetitionReminderManager.canNotify(applicationContext, prefs)) {
            CompetitionReminderManager.showVariedNotification(applicationContext, prefs)
        }
        return Result.success()
    }
}
