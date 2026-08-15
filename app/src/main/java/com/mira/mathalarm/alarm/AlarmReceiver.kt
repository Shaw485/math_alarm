package com.mira.mathalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.mira.mathalarm.R
import com.mira.mathalarm.data.AlarmDataStore
import com.mira.mathalarm.data.AlarmState
import com.mira.mathalarm.service.RingtoneService
import com.mira.mathalarm.ui.home.MainActivity
import com.mira.mathalarm.ui.ringing.RingingActivity
import com.mira.mathalarm.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 闹钟触发广播接收器
 *
 * 关键说明（针对 Android 12+ 国产 ROM）：
 * - 使用 WakeLock 确保 CPU 在闹钟触发瞬间处于唤醒状态，防止 Doze 模式延迟/丢弃广播
 * - 发全屏通知 setFullScreenIntent(pending, true) 拉起答题页
 * - startForegroundService 同时启动响铃服务
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val FULLSCREEN_NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "alarm_fullscreen_channel"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L  // 60秒兜底，防止泄漏

        /**
         * A3 终极拉起答题页方法：
         *   用 startActivities(arrayOf(MainActivity栈底, RingingActivity栈顶)) 构造完整返回栈，
         *   国产ROM会优先检查「任务栈底部是否有用户常用的Launcher Activity」，对合成完整返回栈的
         *   startActivity 请求更宽容，不易被静默拦截。
         *
         * @param attempt 尝试次数（1~3），每次用不同 FLAG 全家桶组合，提高命中概率：
         *   attempt=1: NEW_TASK + CLEAR_TOP + SINGLE_TOP （最常用保守组合）
         *   attempt=2: NEW_TASK + REORDER_TO_FRONT + RESET_TASK_IF_NEEDED （让系统重置任务）
         *   attempt=3: NEW_TASK + CLEAR_TASK + NEW_DOCUMENT + MATCH_EXTERNAL （全新独立文档栈，对自启动白名单最宽容）
         */
        fun bringUpRingingActivity(context: Context, triggerTime: Long, attempt: Int) {
            // 1. 底栈 MainActivity：保证「返回键答题页关闭后回到首页」符合用户预期，且任务栈结构更完整
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }

            // 2. 顶栈 RingingActivity：根据 attempt 号使用 3 种不同 FLAG 组合
            val ringingIntent = Intent(context, RingingActivity::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_TRIGGER_TIME, triggerTime)
                val flags = when (attempt) {
                    1 -> Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_CLEAR_TOP or
                         Intent.FLAG_ACTIVITY_SINGLE_TOP
                    2 -> Intent.FLAG_ACTIVITY_NEW_TASK or
                         Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                         Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    else -> Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                }
                addFlags(flags)
            }

            AppLogger.d(TAG, "bringUpRingingActivity attempt=${attempt}, startActivities合成栈(Main底+Ringing顶), flags=${ringingIntent.flags.toString(16)}")
            // startActivities 一次推入整个合成栈（国产ROM比单独startActivity更易通过）
            context.startActivities(arrayOf(mainIntent, ringingIntent))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d(TAG, "========= 闹钟触发广播收到 =========")
        // v50 调试加强：
        AppLogger.dumpIntent(TAG, "Rcvd#onReceive", intent)
        AppLogger.dumpWakeLockScreen(TAG, "RcvdOnRecv-Start", context)
        AppLogger.dumpAudioStats(TAG, "RcvdOnRecv-Start", context)
        val dataStoreR = AlarmDataStore(context)
        AppLogger.dumpDataStoreAll(TAG, "RcvdOnRecv-Start", dataStoreR)

        // ========== 关键：获取 WakeLock，确保 CPU 唤醒 ==========
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MathAlarm:AlarmWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        AppLogger.event(TAG,
            "WakeLock_acquired" to "Y",
            "WakeLock_hashCode" to wakeLock.hashCode(),
            "WakeLock_isHeld" to wakeLock.isHeld,
            "WakeLock_timeout_ms" to WAKE_LOCK_TIMEOUT_MS,
            "WakeLock_level" to "PARTIAL_WAKE_LOCK(1)")

        // 延长广播接收器生命周期（最长10秒），确保协程里的DataStore写入完成
        val pendingResult = goAsync()

        val triggerTime = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_TRIGGER_TIME, 0L)
        val now = System.currentTimeMillis()
        AppLogger.d(TAG, "intent triggerTime=$triggerTime, now=$now, 延迟=${now - triggerTime}ms")

        // 1. 创建全屏通知渠道
        ensureFullscreenChannel(context)

        // 2. 构造 RingingActivity PendingIntent
        val activityIntent = Intent(context, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AlarmScheduler.EXTRA_ALARM_TRIGGER_TIME, triggerTime)
        }
        val fullscreenPending = PendingIntent.getActivity(
            context,
            100,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. 发全屏通知
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("数学闹钟正在响铃")
            .setContentText("请回答数学题以关闭闹钟")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(fullscreenPending, true)
            .setContentIntent(fullscreenPending)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(FULLSCREEN_NOTIFICATION_ID, notification)
            AppLogger.d(TAG, "全屏通知发送成功 id=$FULLSCREEN_NOTIFICATION_ID（FullScreenIntent交给系统）")
        } catch (e: Exception) {
            AppLogger.e(TAG, "发送全屏通知失败（nm.notify抛异常）", e)
        }

        // ✅ A3: 国产ROM后台启动拦截终极手段：3种不同FLAG策略 × startActivities合成栈
        //    ⚠️ 小米 HyperOS 特征：startActivities() **静默拦截，不抛异常**——"调用成功"≠"Activity真拉起"
        //       所以 1/2/3 必须**全部执行一遍**（绝不能 attempt=1 就 break！否则永远只有一种FLAG组合）
        var raisedAtLeastOnce = false
        AppLogger.eventW(TAG, "A3-bringUpLoop-Start" to "Y", "A3-triggerT" to triggerTime, "A3-nowMs" to System.currentTimeMillis())
        RingingActivity.dumpRuntimePrefs(TAG, "BeforeA3", context)
        for (attempt in 1..3) {
            try {
                bringUpRingingActivity(context, triggerTime, attempt)
                raisedAtLeastOnce = true
                AppLogger.d(TAG, "bringUpRingingActivity 第${attempt}次调用成功（返回无异常）")
                // v50 每次拉完后等 50ms 再 dump SP createdTrigger（异步 onCreate 不会立即写，检测更准）
                try { Thread.sleep(30L) } catch (_: Throwable) {}
                val createdAfterAttempt = RingingActivity.getCreatedTrigger(context)
                AppLogger.event(
                    TAG,
                    "A3-afterAttempt-${attempt}" to "Y",
                    "A3-createdTrigger" to createdAfterAttempt,
                    "A3-matchesExpected" to (createdAfterAttempt == triggerTime),
                    "A3-expectedTrigger" to triggerTime
                )
                // 真的已经 onCreate 成功 → 后续 attempt 跳过（这里才允许 break，和之前的 break 完全不同：是"真拉起成功"才 break）
                if (createdAfterAttempt == triggerTime && triggerTime > 0L) {
                    AppLogger.d(TAG, "A3 attempt=${attempt} 真的 onCreate 成功，后续 attempt 跳过")
                    break
                }
            } catch (t: Throwable) {
                AppLogger.w(TAG, "bringUpRingingActivity 第${attempt}次抛异常（下一轮重试不同FLAG）", t)
            }
        }
        RingingActivity.dumpRuntimePrefs(TAG, "AfterA3", context)
        AppLogger.dumpIntent(TAG, "AfterA3-activityIntent", activityIntent)
        if (!raisedAtLeastOnce) {
            AppLogger.e(TAG, "bringUpRingingActivity 3次全抛异常，用户需点通知栏进入答题页")
        } else {
            AppLogger.event(TAG, "A3-bringUpLoop-End" to "raisedAtLeastOnce=Y")
        }

        // 4. 同步启动 RingtoneService
        val serviceIntent = Intent(context, RingtoneService::class.java).apply {
            action = RingtoneService.ACTION_START_RINGING
            putExtra(AlarmScheduler.EXTRA_ALARM_TRIGGER_TIME, triggerTime)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            AppLogger.d(TAG, "RingtoneService 已请求启动 action=${RingtoneService.ACTION_START_RINGING}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动 RingtoneService 失败", e)
        }

        // 5. 异步写入 DataStore + 【答题页 Ping-pong 双重校验】
        //    国产ROM会静默拦截 startActivity（不抛异常，catch 抓不到，但真实 onCreate 没执行）
        //    这里通过 SP KV「是否真 onCreate」500ms/1500ms 两次轮询检查，不满足就重试
        val dataStore = AlarmDataStore(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dataStore.updateAlarmState(AlarmState.RINGING)
                dataStore.setIsRinging(true)
                AppLogger.d(TAG, "DataStore 写入完成: state=RINGING, isRinging=true")
                AppLogger.dumpDataStoreAll(TAG, "AfterWriteRINGING", dataStore, triggerHumanT = true)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "DataStore 写入异常", t)
            }

            // ========== 【Ping-pong 检查】500ms 第 1 次轮询 ==========
            delay(500)
            AppLogger.dumpWakeLockScreen(TAG, "Ping-500ms-beforeRead", context, extraWl = wakeLock)
            val createdAfter500ms = withContext(Dispatchers.IO) {
                RingingActivity.getCreatedTrigger(context)
            }
            RingingActivity.dumpRuntimePrefs(TAG, "Ping-500ms", context)
            if (createdAfter500ms == triggerTime) {
                AppLogger.eventW(TAG,
                    "Ping-500ms" to "OK",
                    "Ping-500ms-createdTrigger" to createdAfter500ms,
                    "Ping-500ms-expected" to triggerTime,
                    "Ping-500ms-diff-ms" to (System.currentTimeMillis() - triggerTime))
            } else {
                AppLogger.w(TAG, "Ping-pong: 500ms 检查失败（created=$createdAfter500ms ≠ expected=$triggerTime），第2次重试startActivities合成栈（3种FLAG策略全跑一遍）")
                var pingPongOk = false
                for (attempt in 1..3) {
                    try {
                        withContext(Dispatchers.Main.immediate) {
                            bringUpRingingActivity(context, triggerTime, attempt)
                        }
                        pingPongOk = true
                        try { Thread.sleep(30L) } catch (_: Throwable) {}
                        val afterPong = withContext(Dispatchers.IO) { RingingActivity.getCreatedTrigger(context) }
                        AppLogger.event(
                            TAG,
                            "PingPong-afterAttempt-${attempt}" to "Y",
                            "PingPong-createdTrigger" to afterPong,
                            "PingPong-matchesExpected" to (afterPong == triggerTime),
                            "PingPong-expectedTrigger" to triggerTime
                        )
                        if (afterPong == triggerTime && triggerTime > 0L) {
                            AppLogger.d(TAG, "Ping-pong attempt=${attempt} 真的 onCreate 成功，后续 attempt 跳过")
                            break
                        }
                    } catch (t: Throwable) {
                        AppLogger.w(TAG, "Ping-pong bringUpRingingActivity 第${attempt}次抛异常（继续重试）", t)
                    }
                }
                if (!pingPongOk) {
                    AppLogger.e(TAG, "Ping-pong bringUpRingingActivity 3次全抛异常")
                }
                RingingActivity.dumpRuntimePrefs(TAG, "Ping-500ms-RetryDone", context)

                // ========== 【Ping-pong 检查】1500ms 第 2 次轮询 ==========
                delay(1000)
                val createdAfter1500ms = withContext(Dispatchers.IO) {
                    RingingActivity.getCreatedTrigger(context)
                }
                RingingActivity.dumpRuntimePrefs(TAG, "Ping-1500ms", context)
                AppLogger.dumpAudioStats(TAG, "Ping-1500ms-音频", context)
                AppLogger.dumpDataStoreAll(TAG, "Ping-1500ms-DS", dataStore)
                if (createdAfter1500ms == triggerTime) {
                    AppLogger.eventW(TAG,
                        "Ping-1500ms" to "OK",
                        "Ping-1500ms-createdTrigger" to createdAfter1500ms,
                        "Ping-1500ms-diffFromTrigger_ms" to (System.currentTimeMillis() - triggerTime))
                } else {
                    AppLogger.e(TAG, "Ping-pong: 1500ms 仍未拉起答题页！（终极兜底：全屏通知已挂，请用户点通知栏进入答题页）")
                    AppLogger.eventW(TAG,
                        "Ping-1500ms" to "FAIL",
                        "Ping-1500ms-createdTrigger" to createdAfter1500ms,
                        "Ping-1500ms-expectedTrigger" to triggerTime,
                        "Ping-1500ms-FSIntent" to "已挂, 请用户手动点通知栏进入")
                }
            }

            // ========== 所有检查/重试完成，最后释放广播 + WakeLock ==========
            val heldBefore = wakeLock.isHeld
            val tsBefore = System.currentTimeMillis()
            pendingResult.finish()
            if (wakeLock.isHeld) {
                wakeLock.release()
                AppLogger.event(
                    TAG,
                    "Cleanup-all" to "Done",
                    "WakeLock-heldBefore" to heldBefore,
                    "WakeLock-heldAfterRelease" to wakeLock.isHeld,
                    "WakeLock-releaseDelay_ms" to (System.currentTimeMillis() - tsBefore),
                    "PendingResult-finished" to "Y"
                )
            } else {
                AppLogger.eventW(TAG,
                    "Cleanup-all" to "Done",
                    "WakeLock-heldBefore" to "N(已超时释放)",
                    "WakeLock-60sTimeout" to "Y",
                    "PendingResult-finished" to "Y")
            }
        }
    }

    private fun ensureFullscreenChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val exist = manager.getNotificationChannel(CHANNEL_ID)
            if (exist == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "闹钟全屏通知",
                    NotificationManager.IMPORTANCE_HIGH   // IMPORTANCE_HIGH 已是渠道最高级别
                ).apply {
                    description = "闹钟响铃时全屏弹出答题页"
                    setSound(null, null)
                    enableVibration(true)
                    enableLights(true)
                    setBypassDnd(true)    // ✅ 允许绕过勿扰模式（闹钟典型场景）
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
                AppLogger.d(TAG, "全屏通知渠道创建完成: $CHANNEL_ID（允许绕过勿扰）")
            } else {
                // 渠道已存在也强制覆盖关键属性
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (!exist.canBypassDnd()) {
                        exist.setBypassDnd(true)
                        manager.createNotificationChannel(exist)
                        AppLogger.d(TAG, "全屏通知渠道已存在，补上 bypassDnd=true")
                    }
                }
            }
        }
    }
}
