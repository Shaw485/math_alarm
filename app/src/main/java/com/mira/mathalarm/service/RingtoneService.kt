package com.mira.mathalarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.mira.mathalarm.R
import com.mira.mathalarm.alarm.AlarmReceiver
import com.mira.mathalarm.data.AlarmDataStore
import com.mira.mathalarm.data.AlarmState
import com.mira.mathalarm.data.RingtoneOption
import com.mira.mathalarm.overlay.RingingOverlayController
import com.mira.mathalarm.permission.PermissionChecker
import com.mira.mathalarm.ui.ringing.RingingActivity
import com.mira.mathalarm.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 响铃前台服务
 *
 * 关键设计：
 * onStartCommand 本身运行在**主线程**。
 * MediaPlayer 的准备和播放代码必须和 RingtonePickerPopup 的 playRingtone 完全一致（主线程同步执行），
 * 避免任何后台线程、Looper 缺失、afd 关闭时机等厂商 ROM 兼容问题。
 */
class RingtoneService : Service() {

    companion object {
        private const val TAG = "RingtoneService"
        const val ACTION_START_RINGING = "com.mira.mathalarm.START_RINGING"
        const val ACTION_STOP_RINGING = "com.mira.mathalarm.STOP_RINGING"
        const val EXTRA_RINGING_ACTIVITY_VISIBLE = "extra_ringing_activity_visible"
        const val NOTIFICATION_ID = 1001
        const val FULLSCREEN_NOTIFICATION_ID = com.mira.mathalarm.alarm.AlarmReceiver.FULLSCREEN_NOTIFICATION_ID // 1002
        const val CHANNEL_ID = "alarm_channel"
        private const val MAX_RINGING_DURATION_MS = 10 * 60 * 1000L

        /**
         * Bug2 修复：强制关闭所有响铃相关组件（任何清闹钟场景统一调用）
         * 包含三件事：
         * 1) stopService 杀 RingtoneService（MediaPlayer 停止、前台移除、onDestroy释放）
         * 2) NotificationManager 取消两条通知：前台服务(1001) + 全屏通知(1002)
         * 3) 清理 SharedPreferences 里 RingingActivity.onCreate 标记（下次重新设置闹钟可重新检测）
         */
        fun forceStopRingingAndNotify(context: Context) {
            val tags = "forceStopRingingAndNotify"
            try {
                // 1) 停 Service（Service onDestroy 会再 stopRinging 释放 MediaPlayer）
                context.stopService(Intent(context, RingtoneService::class.java))
                AppLogger.d(TAG, "$tags: stopService(RingtoneService) 调用完成")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "$tags: stopService 异常", t)
            }
            try {
                // 2) 取消所有相关通知（前台服务通知 + 全屏大图通知，双 id 保险）
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIFICATION_ID)
                nm.cancel(FULLSCREEN_NOTIFICATION_ID)
                AppLogger.d(TAG, "$tags: 已取消通知 id=$NOTIFICATION_ID (前台服务), id=$FULLSCREEN_NOTIFICATION_ID (全屏)")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "$tags: cancel 通知异常", t)
            }
            try {
                // 3) 清理 SP 的答题页 onCreate 标记（为下一轮重新检测做准备）
                RingingActivity.clearCreatedTrigger(context)
                AppLogger.d(TAG, "$tags: 已清理 RingingActivity.onCreate SP 标记")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "$tags: 清理 SP 标记异常", t)
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dataStore: AlarmDataStore
    /** A1 终极兜底：WindowManager 全屏悬浮答题页（仅在 canDrawOverlays=true 时生效） */
    private var overlayController: RingingOverlayController? = null
    /** v51 防重入：记录当前已经 schedule 的 1.5s/2.5s/MP 检查 runnable（避免 startId=2 二次 START_RINGING 重复挂 N 次 handler） */
    private var scheduled1500: Runnable? = null
    private var scheduled2500: Runnable? = null
    private var scheduledMP300: Runnable? = null
    private var scheduledMP1000: Runnable? = null
    /** v51 幂等性：同一 triggerTime 的 START_RINGING 只执行一次响铃；不同 triggerTime 才重置 */
    private var lastStartedTrigger: Long = -1L
    private var lastStartedAtMs: Long = 0L

    private val timeoutRunnable = Runnable {
        AppLogger.d(TAG, "响铃达到10分钟上限（MAX_RINGING_DURATION_MS=$MAX_RINGING_DURATION_MS），自动停止")
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.clearAlarm()
            AppLogger.d(TAG, "clearAlarm() 调用完成（DataStore状态已回到NOT_SET）")
        }
        stopRinging()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        dataStore = AlarmDataStore(this)
        overlayController = RingingOverlayController(this)
        createNotificationChannel()
        AppLogger.d(TAG, "onCreate: Service 创建（overlayController已初始化）")
        AppLogger.dumpWakeLockScreen(TAG, "Svc-onCreate", this)
        AppLogger.dumpDataStoreAll(TAG, "Svc-onCreate", dataStore)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Activity 可能比 2.5s Overlay 兜底更晚才被 HyperOS 放行。一旦 Activity 真正
        // onCreate，必须立即撤掉已经显示的 Overlay，否则会叠两道题、要求用户答两次。
        if (intent?.getBooleanExtra(EXTRA_RINGING_ACTIVITY_VISIBLE, false) == true) {
            AppLogger.eventW(TAG,
                "RingingActivityVisible" to "HIDE_OVERLAY_NOW",
                "startId" to startId,
                "intentAction" to (intent.action ?: "null"))
            overlayController?.hide()
        }
        // v52：START_STICKY 在进程被国产 ROM 重建时会回调 null Intent。
        // 旧逻辑直接忽略，导致 MediaPlayer、1.5s Activity 重试和2.5s Overlay兜底全部丢失。
        // 若 DataStore 仍处在有效响铃窗口，就把 null Intent 恢复成一次标准 START_RINGING。
        val resolvedAction = if (intent?.action == null) {
            val stateNow = runBlocking { runCatching { dataStore.alarmState.first() }.getOrDefault(AlarmState.NOT_SET) }
            val isRingingNow = runBlocking { runCatching { dataStore.isRinging.first() }.getOrDefault(false) }
            val triggerNow = runBlocking { runCatching { dataStore.alarmTriggerTime.first() }.getOrDefault(0L) }
            val sinceTriggerMs = if (triggerNow > 0L) System.currentTimeMillis() - triggerNow else Long.MAX_VALUE
            val recoverable = stateNow == AlarmState.RINGING && isRingingNow &&
                sinceTriggerMs in -60_000L..(MAX_RINGING_DURATION_MS + 30_000L)
            AppLogger.eventW(TAG,
                "Svc-nullIntentRecovery" to if (recoverable) "RECOVER_AS_START_RINGING" else "IGNORE_STALE_AND_STOP",
                "state" to stateNow.name,
                "isRinging" to isRingingNow,
                "triggerTime" to triggerNow,
                "sinceTrigger_ms" to sinceTriggerMs,
                "startId" to startId)
            if (recoverable) ACTION_START_RINGING else null
        } else intent.action
        AppLogger.event(TAG,
            "Svc-onStartCommand" to "START",
            "intent_action" to (intent?.action ?: "null"),
            "resolved_action" to (resolvedAction ?: "null"),
            "start_flags" to flags,
            "startId" to startId)
        AppLogger.dumpIntent(TAG, "Svc-onStart", intent)
        AppLogger.dumpAudioStats(TAG, "Svc-preRinging", this)
        when (resolvedAction) {
            ACTION_START_RINGING -> {
                AppLogger.d(TAG, "【ACTION_START_RINGING 分支匹配成功】开始执行")
                val svcStartTs = System.currentTimeMillis()
                // v53：第三通路由 AlarmManager 直接启动 Service，不经过 AlarmReceiver，
                // 因此 Service 必须自行把 ACTIVE 原子推进到 RINGING，确保进程重建/首页恢复逻辑拥有正确持久状态。
                val stateBeforeStart = runBlocking {
                    runCatching { dataStore.alarmState.first() }.getOrDefault(AlarmState.NOT_SET)
                }
                if (stateBeforeStart == AlarmState.ACTIVE) {
                    runBlocking {
                        dataStore.updateAlarmState(AlarmState.RINGING)
                        dataStore.setIsRinging(true)
                    }
                    AppLogger.eventW(TAG,
                        "DirectServiceStatePromotion" to "ACTIVE_TO_RINGING",
                        "startId" to startId)
                }
                // ================================================================
                // v51 BugA 修复：START_RINGING 重入保护（小米 HyperOS 双通路 setAlarmClock + setExactAndAllowWhileIdle 可能在 1.6s 内都触发，
                //   上一轮 startId=1 的 MP 刚 start() 1.5s 后就被 startId=2 进来的 "旧 MediaPlayer 已释放" 干掉 → 听着就是"响一下就没了"）
                // 幂等判定 = 2 条任一成立就跳过响铃逻辑：
                //   ① mediaPlayer != null 且 isPlaying=true（说明 1.5s 内前一轮已经正常响了）
                //   ② 同一 triggerTime 且距离上次启动 < 3 秒（绝对防重入兜底）
                // ================================================================
                val mpPlaying = runCatching { mediaPlayer?.isPlaying }.getOrDefault(false) == true
                val dsTriggerNow = runBlocking { runCatching { dataStore.alarmTriggerTime.first() }.getOrDefault(0L) }
                val sameTriggerWithin3s = (lastStartedTrigger != -1L && lastStartedTrigger == dsTriggerNow &&
                    (System.currentTimeMillis() - lastStartedAtMs) < 3000L)
                val skipRinging = mpPlaying || sameTriggerWithin3s
                if (skipRinging) {
                    AppLogger.eventW(TAG,
                        "Svc-START_RINGING" to "SKIP-DUP(重入跳过)",
                        "mpPlaying" to mpPlaying,
                        "sameTriggerWithin3s" to sameTriggerWithin3s,
                        "lastStartedTrigger" to lastStartedTrigger,
                        "dsTriggerNow" to dsTriggerNow,
                        "elapsedSinceLastStart_ms" to (System.currentTimeMillis() - lastStartedAtMs),
                        "startId" to startId)
                }
                if (!skipRinging) {
                    lastStartedTrigger = dsTriggerNow
                    lastStartedAtMs = System.currentTimeMillis()
                    try {
                    // ① 先构建 Notification（单独 try-catch：构建失败就用最简版兜底）
                    val notification = try {
                        buildNotification().also { AppLogger.d(TAG, "buildNotification() 构建成功") }
                    } catch (t: Throwable) {
                        AppLogger.e(TAG, "buildNotification() 失败，用最简 Notification 兜底", t)
                        NotificationCompat.Builder(this, CHANNEL_ID)
                            .setContentTitle("闹钟响铃中")
                            .setSmallIcon(R.drawable.ic_alarm)
                            .setOngoing(true)
                            .setSound(null)
                            .build()
                    }

                    // ② Android 14+ (API 34) 强制要求 3 参数版 startForeground
                    // ⚠️ 直接写 int 字面量，禁止引用 ServiceInfo.* 常量（防止某些厂商ROM类加载VerifyError直接崩进程）
                    // FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 2
                    // FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1073741824 (1<<30)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        AppLogger.d(TAG, "startForeground(3参数版): typeInt=${2 or 1073741824}")
                        startForeground(NOTIFICATION_ID, notification, 2 or 1073741824)
                    } else {
                        AppLogger.d(TAG, "startForeground(2参数版): API<34")
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    AppLogger.event(TAG,
                        "startForeground" to "OK",
                        "notif_id" to NOTIFICATION_ID,
                        "took_ms" to (System.currentTimeMillis() - svcStartTs))

                    // ③ 主线程同步执行播放，完全复刻 RingtonePickerPopup 的代码
                    val beforeRinging = System.currentTimeMillis()
                    startRinging()
                    AppLogger.d(TAG, "startRinging() 同步返回 took=${System.currentTimeMillis() - beforeRinging}ms")

                    // ④ 设置 10 分钟兜底停止
                    handler.removeCallbacks(timeoutRunnable)
                    handler.postDelayed(timeoutRunnable, MAX_RINGING_DURATION_MS)
                    AppLogger.event(TAG, "10minStopTimer" to "armed", "delay_ms" to MAX_RINGING_DURATION_MS)

                    // v51 BugC：防止 startId=2 重入时重复 schedule 多份 1.5s/2.5s/MP 检查
                    scheduled1500?.let { handler.removeCallbacks(it) }
                    scheduled2500?.let { handler.removeCallbacks(it) }
                    scheduledMP300?.let { handler.removeCallbacks(it) }
                    scheduledMP1000?.let { handler.removeCallbacks(it) }

                    // ✅ A3+A1 终极兜底：1.5s 没起来就 3×startActivities 合成栈；2.5s 还没起来就 WindowManager 全屏悬浮窗
                    //    前台 Service 拉起 Activity 的权限豁免等级比 BroadcastReceiver 高，国产 ROM 杀 App 场景下命中率最高
                    scheduled1500 = Runnable {
                        try {
                            val createdTrigger = RingingActivity.getCreatedTrigger(this)
                            val dataStoreTrigger = runBlocking { dataStore.alarmTriggerTime.first() }
                            val stateNow = runBlocking { dataStore.alarmState.first() }
                            val isRing = runBlocking { dataStore.isRinging.first() }
                            RingingActivity.dumpRuntimePrefs(TAG, "Svc-1.5s", this)
                            AppLogger.eventW(TAG,
                                "Svc-1.5sCheck" to "START",
                                "createdTrigger" to createdTrigger,
                                "dsTrigger" to dataStoreTrigger,
                                "match" to (createdTrigger == dataStoreTrigger && dataStoreTrigger > 0L),
                                "dsState" to stateNow.name,
                                "dsIsRinging" to isRing,
                                "diff_ms" to (System.currentTimeMillis() - dataStoreTrigger))
                            if (createdTrigger == dataStoreTrigger && dataStoreTrigger > 0L) {
                                AppLogger.d(TAG, "前台Service兜底1.5s：答题页已onCreate，无需再拉（created=$createdTrigger）")
                            } else {
                                AppLogger.w(TAG, "前台Service兜底1.5s：仍未onCreate（created=$createdTrigger,ds=$dataStoreTrigger），3次startActivities合成栈重试（⚠️3种FLAG全跑一遍，不break）")
                                // 和 AlarmReceiver 用同一个 helper：attempt 1~3 三种 FLAG 全家桶
                                // ⚠️ 小米 HyperOS 静默拦截不抛异常：1/2/3 必须全部执行，不能 attempt=1 break
                                for (attempt in 1..3) {
                                    try {
                                        AlarmReceiver.bringUpRingingActivity(this, dataStoreTrigger, attempt)
                                        AppLogger.d(TAG, "前台Service bringUpRingingActivity 第${attempt}次调用成功")
                                        try { Thread.sleep(30L) } catch (_: Throwable) {}
                                        val afterAtt = RingingActivity.getCreatedTrigger(this@RingtoneService)
                                        AppLogger.event(TAG,
                                            "Svc-1.5s-afterAtt-${attempt}" to "Y",
                                            "afterAtt" to afterAtt,
                                            "matches" to (afterAtt == dataStoreTrigger))
                                        if (afterAtt == dataStoreTrigger && dataStoreTrigger > 0L) {
                                            AppLogger.d(TAG, "Svc 1.5s attempt=${attempt} 真的 onCreate 成功，后续 attempt 跳过")
                                            break
                                        }
                                    } catch (t: Throwable) {
                                        AppLogger.w(TAG, "前台Service bringUpRingingActivity 第${attempt}次抛异常（继续重试）", t)
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            AppLogger.e(TAG, "前台Service兜底1.5s：异常（忽略）", t)
                        }
                    }.also { handler.postDelayed(it, 1500L) }

                    // ✅ A1：2.5 秒还没 onCreate + 已经授了悬浮窗权限 = WindowManager TYPE_APPLICATION_OVERLAY 全屏答题页
                    // 这一步是「国产ROM拦截后台 startActivity」的核武器——不依赖 Activity 启动机制，直接通过系统窗口层画 UI，
                    // 100% 能显示在锁屏/任何 App 之上（只要 canDrawOverlays=true）
                    scheduled2500 = Runnable {
                        try {
                            val createdTrigger = RingingActivity.getCreatedTrigger(this)
                            val dataStoreTrigger = runBlocking { dataStore.alarmTriggerTime.first() }
                            val stateNow = runBlocking { dataStore.alarmState.first() }
                            RingingActivity.dumpRuntimePrefs(TAG, "Svc-2.5s", this)
                            AppLogger.dumpAudioStats(TAG, "Svc-2.5s-音频", this)
                            AppLogger.dumpWakeLockScreen(TAG, "Svc-2.5s-screen", this)
                            AppLogger.eventW(TAG,
                                "Svc-2.5sCheck" to "START",
                                "createdTrigger" to createdTrigger,
                                "dsTrigger" to dataStoreTrigger,
                                "match" to (createdTrigger == dataStoreTrigger && dataStoreTrigger > 0L),
                                "dsState" to stateNow.name,
                                "canDrawOverlays" to PermissionChecker.canDrawOverlays(this),
                                "overlayCtrl" to (overlayController != null))
                            if (createdTrigger == dataStoreTrigger && dataStoreTrigger > 0L) {
                                AppLogger.d(TAG, "前台Service兜底2.5s：答题页已onCreate，Overlay无需显示")
                            } else if (PermissionChecker.canDrawOverlays(this)) {
                                AppLogger.w(TAG, "前台Service兜底2.5s：仍未onCreate且canDrawOverlays=true！【终极兜底】显示WindowManager全屏悬浮答题页（TYPE_APPLICATION_OVERLAY）")
                                overlayController?.show(triggerTime = dataStoreTrigger)
                            } else {
                                AppLogger.e(TAG, "前台Service兜底2.5s：仍未onCreate，但未授予悬浮窗权限（显示悬浮窗），Overlay兜底失败。请用户点通知栏或手动打开App进入答题页。")
                            }
                        } catch (t: Throwable) {
                            AppLogger.e(TAG, "前台Service兜底2.5s Overlay显示异常", t)
                        }
                    }.also { handler.postDelayed(it, 2500L) }
                    } catch (t: Throwable) {
                        // ✅ 终极强兜底：任何异常都先尝试 startRinging 响铃，绝不让用户听不到
                        AppLogger.e(TAG, "ACTION_START_RINGING 外层catch！立刻降级仅响铃", t)
                        try {
                            startRinging()
                            handler.removeCallbacks(timeoutRunnable)
                            handler.postDelayed(timeoutRunnable, MAX_RINGING_DURATION_MS)
                            AppLogger.d(TAG, "终极降级响铃成功，10分钟兜底定时器已启动")
                        } catch (t2: Throwable) {
                            AppLogger.e(TAG, "终极降级响铃也失败了", t2)
                        }
                    }
                }
            }
            ACTION_STOP_RINGING -> {
                AppLogger.d(TAG, "收到 ACTION_STOP_RINGING，停止播放")
                try {
                    stopRinging()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "ACTION_STOP_RINGING 异常（忽略）", t)
                }
            }
            null -> {
                AppLogger.w(TAG, "null Intent 不在有效响铃窗口，停止空壳 Service，避免首页永久显示响铃中")
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    /**
     * 开始播放铃声
     * ⚠️ 必须运行在主线程。代码顺序和 RingtonePickerPopup.playRingtone 完全一致，确保兼容性。
     */
    private fun startRinging() {
        // 1. 同步读取 DataStore：runBlocking 阻塞当前线程（主线程）几百毫秒即可，
        //    Service onStartCommand 被阻塞几百毫秒完全不会 ANR（ANR 阈值前台服务 20s）
        val ringtoneOption: RingtoneOption = try {
            runBlocking { dataStore.ringtoneOption.first() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取铃声偏好失败，使用默认铃声（清晨）", e)
            RingtoneOption.QINGCHEN
        }
        val resId = ringtoneOption.resId
        var volume = ringtoneOption.volumeScale
        AppLogger.d(TAG, "准备播放: 铃声=${ringtoneOption.displayName}, resId=$resId, volumeScale=$volume")

        // ========== 🔴 v49.0 关键修复：STREAM_ALARM 拉满 + STREAM_ALARM绑定 ==========
        // 用户侧"没听见铃声"= USAGE_ALARM 在部分小米 HyperOS 上映射到媒体流（用户媒体静音了）
        // 1. 强制把 STREAM_ALARM（系统闹钟音量流）拉到 max：独立于媒体静音，用户能听到
        // 2. 同时用废弃的 setAudioStreamType(STREAM_ALARM) 兜底绑定到闹钟流（部分厂商仍吃这个）
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentAlarmVol < alarmMax) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, alarmMax, 0)
                AppLogger.d(TAG, "STREAM_ALARM 已从 $currentAlarmVol 拉满到 max=$alarmMax（独立于媒体静音）")
            } else {
                AppLogger.d(TAG, "STREAM_ALARM 已经是 max=$alarmMax，无需调整")
            }
            // 媒体流也顺手拉到 70%，避免 USAGE_ALARM 在该机型上走媒体流时听不到
            val mediaMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val mediaTarget = (mediaMax * 0.85f).toInt().coerceAtLeast(1)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, mediaTarget, 0)
            AppLogger.d(TAG, "STREAM_MUSIC 兜底拉到 $mediaTarget/$mediaMax（防止 USAGE_ALARM 在该机型映射到媒体流）")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "拉 STREAM_ALARM/MUSIC 音量异常（忽略）", t)
        }
        // 把 volumeScale 拉到 1.0 兜底，用户侧设置的 0.7 不够响
        if (volume < 1.0f) {
            AppLogger.w(TAG, "响铃兜底：volumeScale=$volume → 强制置为 1.0 保证听得见")
            volume = 1.0f
        }

        // 2. 先释放上一个播放器（如果有）
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            AppLogger.d(TAG, "旧 MediaPlayer 已释放")
        } catch (e: Exception) {
            AppLogger.e(TAG, "释放旧 MediaPlayer 出错（忽略）", e)
        }

        // 3. 全新创建 MediaPlayer，步骤和 RingtonePickerPopup.playRingtone 完全一致
        val mp: MediaPlayer? = try {
            val player = MediaPlayer()
            // 双保险：先废弃的 setAudioStreamType(STREAM_ALARM) → 再新 API setAudioAttributes
            // ⚠️ 顺序必须先 setAudioStreamType 再 setAudioAttributes，否则后者被覆盖
            @Suppress("DEPRECATION")
            try {
                player.setAudioStreamType(AudioManager.STREAM_ALARM)
                AppLogger.d(TAG, "setAudioStreamType: STREAM_ALARM OK (老API兜底，绑定到系统闹钟流)")
            } catch (t: Throwable) {
                AppLogger.w(TAG, "setAudioStreamType 废弃API调用异常（忽略，继续走新API）", t)
            }
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)  // ⚠️ MUSIC→SONIFICATION：更贴近"闹钟提醒语义"，HyperOS 会优先分到闹钟流
                    .build()
            )
            AppLogger.d(TAG, "setAudioAttributes: USAGE_ALARM + CONTENT_TYPE_SONIFICATION OK")
            player.isLooping = true
            AppLogger.d(TAG, "isLooping = true OK")

            val afd = resources.openRawResourceFd(resId)
            AppLogger.d(TAG, "openRawResourceFd: length=${afd?.length ?: -1}, startOffset=${afd?.startOffset ?: -1}")
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            AppLogger.d(TAG, "setDataSource + afd.close OK")

            player.prepare()
            AppLogger.d(TAG, "prepare() OK")

            player.setVolume(volume, volume)
            AppLogger.d(TAG, "setVolume($volume, $volume) OK")

            player.start()
            AppLogger.d(TAG, "start() 调用完成, isPlaying=${player.isPlaying}")
            AppLogger.dumpRingtoneAndPlayer(TAG, "MP-afterStart-0ms", ringtoneOption, player)

            // v50 关键调试：start 之后 300ms / 1000ms 再打一次 isPlaying/curPos
            // 部分 HyperOS 机型 start() 同步返回 isPlaying=true，但 200ms 内被"音频焦点抢占"静默 pause
            val postStartMp = player
            val postStartRingtone = ringtoneOption
            val tsPostStart = System.currentTimeMillis()
            scheduledMP300 = Runnable {
                runCatching {
                    AppLogger.dumpRingtoneAndPlayer(TAG, "MP-afterStart-300ms", postStartRingtone, postStartMp)
                    AppLogger.dumpAudioStats(TAG, "MP-afterStart-300ms", this@RingtoneService)
                }.onFailure { AppLogger.w(TAG, "MP-afterStart-300ms dump 异常", it) }
            }.also { handler.postDelayed(it, 300L) }
            scheduledMP1000 = Runnable {
                runCatching {
                    AppLogger.dumpRingtoneAndPlayer(TAG, "MP-afterStart-1000ms", postStartRingtone, postStartMp)
                    val curPlaying = runCatching { postStartMp.isPlaying }.getOrDefault(false)
                    if (!curPlaying) {
                        AppLogger.eventW(TAG,
                            "MP-afterStart-1000ms" to "FAIL-SILENT(静默停了)",
                            "sinceStart_ms" to (System.currentTimeMillis() - tsPostStart),
                            "recovery_retry_forceStart" to "Y")
                        runCatching {
                            postStartMp.setVolume(1.0f, 1.0f)
                            postStartMp.start()
                            AppLogger.dumpRingtoneAndPlayer(TAG, "MP-afterStart-1000ms-retry", postStartRingtone, postStartMp)
                        }.onFailure { AppLogger.e(TAG, "1000ms retry start 异常", it) }
                    } else {
                        AppLogger.event(TAG, "MP-afterStart-1000ms" to "OK", "sinceStart_ms" to (System.currentTimeMillis() - tsPostStart))
                    }
                }.onFailure { AppLogger.w(TAG, "MP-afterStart-1000ms dump 异常", it) }
            }.also { handler.postDelayed(it, 1000L) }

            // 终极兜底：start 后仍没声音，立刻降级用 MediaPlayer.create()（兼容性模式）
            if (!player.isPlaying) {
                AppLogger.w(TAG, "start 后 isPlaying=false，尝试 MediaPlayer.create 兜底方式")
                player.release()
                val fallback = MediaPlayer.create(this, resId)
                fallback?.apply {
                    setVolume(volume, volume)
                    isLooping = true
                    start()
                    AppLogger.d(TAG, "兜底方式 MediaPlayer.create 播放, isPlaying=${isPlaying}")
                }
                fallback
            } else {
                player
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "主线程同步播放失败", e)
            try {
                // 异常兜底：MediaPlayer.create 静态方法（最保守的方式）
                AppLogger.w(TAG, "使用 MediaPlayer.create 兜底尝试")
                val fallback = MediaPlayer.create(this, resId)
                fallback?.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setVolume(volume, volume)
                    isLooping = true
                    start()
                    AppLogger.d(TAG, "异常兜底 create 播放成功 isPlaying=${isPlaying}")
                }
                fallback
            } catch (e2: Exception) {
                AppLogger.e(TAG, "兜底方式也失败了", e2)
                null
            }
        }

        mediaPlayer = mp
        AppLogger.d(TAG, "startRinging 结束，mediaPlayer!=null = ${mp != null}")
    }

    /**
     * 停止播放铃声
     */
    private fun stopRinging() {
        // v51 BugC：stopRinging 时把所有 scheduled 的 handler runnable 全清（防止 Service stopSelf 后 1.5s/2.5s/MP 检查仍然触发）
        handler.removeCallbacks(timeoutRunnable)
        scheduled1500?.let { handler.removeCallbacks(it) }; scheduled1500 = null
        scheduled2500?.let { handler.removeCallbacks(it) }; scheduled2500 = null
        scheduledMP300?.let { handler.removeCallbacks(it) }; scheduledMP300 = null
        scheduledMP1000?.let { handler.removeCallbacks(it) }; scheduledMP1000 = null
        lastStartedTrigger = -1L
        lastStartedAtMs = 0L
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                    AppLogger.d(TAG, "stop() 已停止播放")
                }
                release()
            }
            mediaPlayer = null
            AppLogger.d(TAG, "铃声已停止，播放器已释放")
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止铃声失败", e)
        }
    }

    /**
     * 构建前台服务通知（锁屏点击跳转答题页）
     */
    private fun buildNotification(): Notification {
        val intent = Intent(this, RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("闹钟响铃中")
            .setContentText("请回答数学题以关闭闹钟")
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setSound(null)
            .build()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     * 注意 setSound(null) 是禁用通知渠道自带的提示音，铃声由 Service 单独播放
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "闹钟通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃通知"
                setSound(null, null)
                enableVibration(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            AppLogger.d(TAG, "通知渠道已创建: $CHANNEL_ID")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // A1: Service销毁前先移除悬浮答题窗（若存在，防止用户杀Service后悬浮窗残留在屏幕上）
        try {
            overlayController?.hide()
            overlayController = null
            AppLogger.d(TAG, "onDestroy: overlayController已移除")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "onDestroy: overlayController移除异常（忽略）", t)
        }
        stopRinging()
        super.onDestroy()
        AppLogger.d(TAG, "onDestroy: Service 销毁")
    }
}
