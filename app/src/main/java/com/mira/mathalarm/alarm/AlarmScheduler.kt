package com.mira.mathalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mira.mathalarm.data.AlarmDataStore
import com.mira.mathalarm.data.AlarmState
import com.mira.mathalarm.service.RingtoneService
import com.mira.mathalarm.util.AppLogger
import kotlinx.coroutines.flow.first
import java.util.Calendar

/**
 * 闹钟调度管理器
 * 使用 AlarmManager.setAlarmClock() 设置精确闹钟，保证 Doze 模式下也能准时触发
 * 负责闹钟的设置、取消、重排等调度操作
 */
class AlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "AlarmScheduler"
        const val REQUEST_CODE_ALARM = 1001
        const val REQUEST_CODE_ALARM_EXACT = 1002  // 第二个独立 PendingIntent：setExactAndAllowWhileIdle 兜底
        const val REQUEST_CODE_ALARM_SERVICE = 1003 // 第三个独立 PendingIntent：系统直接启动前台响铃Service，绕过ROM广播拦截
        const val EXTRA_ALARM_TRIGGER_TIME = "extra_alarm_trigger_time"
        private const val MISSED_ALARM_RECOVERY_WINDOW_MS = 10 * 60 * 1000L

        /** setAlarm 返回值：表示设置失败（无权限 / 其他异常） */
        const val SET_ALARM_FAILED: Long = -1L
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val dataStore = AlarmDataStore(context)

    /** 构建 setAlarmClock 用的 PendingIntent（闹钟栏显示用 + 主触发通路） */
    private fun buildAlarmPendingIntent(triggerTime: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_TRIGGER_TIME, triggerTime)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 构建 setExactAndAllowWhileIdle 兜底用的 PendingIntent（独立 requestCode，防止 Doze/杀进程后主通路失效） */
    private fun buildExactAlarmPendingIntent(triggerTime: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_TRIGGER_TIME, triggerTime)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM_EXACT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 第三通路：AlarmManager 到点直接启动 RingtoneService。
     * 部分 HyperOS 版本会保留 nextAlarmClock 记录却静默不投递 BroadcastReceiver；
     * 使用独立的 foreground-service PendingIntent 可绕过这一层厂商广播拦截。
     */
    private fun buildRingingServicePendingIntent(triggerTime: Long): PendingIntent {
        val intent = Intent(context, RingtoneService::class.java).apply {
            action = RingtoneService.ACTION_START_RINGING
            putExtra(EXTRA_ALARM_TRIGGER_TIME, triggerTime)
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, REQUEST_CODE_ALARM_SERVICE, intent, pendingFlags)
        } else {
            PendingIntent.getService(context, REQUEST_CODE_ALARM_SERVICE, intent, pendingFlags)
        }
    }

    /**
     * 设置闹钟（双保险挂两次，哪个先触发哪个生效，AlarmReceiver 内部用 state==RINGING 防重入）
     * 通路 1：setAlarmClock() —— 走系统闹钟栏，优先级最高、Doze豁免
     * 通路 2：setExactAndAllowWhileIdle() —— 独立 API，部分国产 ROM 杀进程后主通路失效时兜底
     * @param hour 小时（0-23）
     * @param minute 分钟（0-59）
     * @return 闹钟触发时间戳（毫秒），失败（无权限/异常）返回 SET_ALARM_FAILED(-1)
     */
    suspend fun setAlarm(hour: Int, minute: Int): Long {
        val triggerTime = calculateNextTriggerTime(hour, minute)
        AppLogger.d(TAG, "setAlarm 请求: hour=$hour, minute=$minute, 计算的triggerTime=$triggerTime (约 ${(triggerTime - System.currentTimeMillis()) / 1000}秒后触发)")

        val alarmPi = buildAlarmPendingIntent(triggerTime)
        val exactPi = buildExactAlarmPendingIntent(triggerTime)
        val servicePi = buildRingingServicePendingIntent(triggerTime)
        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, alarmPi)

        // ========== 通路 1：setAlarmClock（系统闹钟栏显示通路，官方优先级最高） ==========
        try {
            alarmManager.setAlarmClock(alarmClockInfo, alarmPi)
            AppLogger.d(TAG, "setAlarmClock 成功: ${hour}:${String.format("%02d", minute)}, triggerTime=$triggerTime")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "setAlarmClock 失败，缺少精确闹钟权限（SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM）", e)
            return SET_ALARM_FAILED
        } catch (e: Exception) {
            AppLogger.e(TAG, "setAlarmClock 失败，未知异常", e)
            return SET_ALARM_FAILED
        }

        // ========== 通路 2：setExactAndAllowWhileIdle（Doze+杀进程兜底通路，独立 PendingIntent） ==========
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    exactPi
                )
                AppLogger.d(TAG, "setExactAndAllowWhileIdle 兜底通路设置成功, requestCode=$REQUEST_CODE_ALARM_EXACT")
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, exactPi)
                AppLogger.d(TAG, "setExact 兜底通路设置成功（API<23）")
            }
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "兜底通路 setExact* 权限异常，仅依赖主通路 setAlarmClock", e)
            // 只兜底通路失败不返回 SET_ALARM_FAILED（主通路已经挂进去了），继续往下走
        } catch (e: Exception) {
            AppLogger.w(TAG, "兜底通路 setExact* 未知异常，仅依赖主通路 setAlarmClock", e)
        }

        // ========== 通路 3：直接启动前台响铃Service（绕过HyperOS广播静默拦截） ==========
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, servicePi)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, servicePi)
            }
            AppLogger.eventW(TAG,
                "DirectServiceAlarm" to "SCHEDULED",
                "requestCode" to REQUEST_CODE_ALARM_SERVICE,
                "triggerTime" to triggerTime)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "第三通路：直接启动RingtoneService调度失败，仍保留前两条通路", t)
        }

        // ========== 设置后立刻反查：验证系统闹钟栏是否真的挂了我们的闹钟 ==========
        try {
            val nextClock = alarmManager.nextAlarmClock
            if (nextClock == null) {
                AppLogger.w(TAG, "【警告】设置后反查 nextAlarmClock 为 null！（国产 ROM 可能没真的注册闹钟，杀 App 后会失效）")
            } else {
                val delta = nextClock.triggerTime - triggerTime
                AppLogger.d(TAG, "反查 nextAlarmClock 成功: triggerTime=${nextClock.triggerTime}, 与设定差值=${delta}ms (允许±1分钟误差均为正常)")
                if (kotlin.math.abs(delta) > 60_000L) {
                    AppLogger.w(TAG, "【警告】nextAlarmClock.triggerTime 与设定的 triggerTime 差超过 1 分钟（${delta/1000}秒），可能被系统篡改或有其他闹钟插入")
                }
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "反查 nextAlarmClock 异常（忽略，不影响闹钟设置）", t)
        }

        // ⚠️ 只有 setAlarmClock 主通路成功了才写入 DataStore
        dataStore.saveAlarm(hour, minute, triggerTime)
        AppLogger.d(TAG, "DataStore.saveAlarm 写入完成")

        return triggerTime
    }

    /**
     * 取消当前闹钟（同时取消 setAlarmClock 和 setExact* 两条 PendingIntent）
     * 并强制关闭任何可能残留的响铃服务和通知
     */
    suspend fun cancelAlarm() {
        val alarmPi = buildAlarmPendingIntent(0L)
        val exactPi = buildExactAlarmPendingIntent(0L)
        val servicePi = buildRingingServicePendingIntent(0L)

        alarmManager.cancel(alarmPi)
        alarmManager.cancel(exactPi)
        alarmManager.cancel(servicePi)
        AppLogger.d(TAG, "AlarmManager.cancel(三PendingIntent) 调用完成，闹钟已取消（AlarmClock+Exact广播+DirectService）")

        // ✅ Bug2 修复：cancelAlarm 时也强制停铃+关通知（防止任何残留）
        com.mira.mathalarm.service.RingtoneService.forceStopRingingAndNotify(context)

        dataStore.updateAlarmState(AlarmState.NOT_SET)
        AppLogger.d(TAG, "DataStore.updateAlarmState(NOT_SET) 已完成")
    }

    /**
     * 将闹钟设为失效（用户主动失效）
     */
    suspend fun disableAlarm() {
        val alarmPi = buildAlarmPendingIntent(0L)
        val exactPi = buildExactAlarmPendingIntent(0L)
        val servicePi = buildRingingServicePendingIntent(0L)

        alarmManager.cancel(alarmPi)
        alarmManager.cancel(exactPi)
        alarmManager.cancel(servicePi)
        AppLogger.d(TAG, "用户点「失效」：三PendingIntent已从AlarmManager取消")

        // ✅ Bug2 修复：用户点失效时也强制停铃（防止上一轮铃还在响的情况）
        com.mira.mathalarm.service.RingtoneService.forceStopRingingAndNotify(context)

        dataStore.updateAlarmState(AlarmState.DISABLED)
        AppLogger.d(TAG, "DataStore.updateAlarmState(DISABLED) 已完成，3秒后会回流NOT_SET")
    }

    /**
     * 计算下一次触发时间
     * 如果当前时间的「时:分」已经严格超过设定时间，则为明天同一时间
     * 注意：使用「只比较到分钟粒度」的方式，避免 15:26:30 设置 15:26 被判为已过
     */
    fun calculateNextTriggerTime(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 只比较到「分钟级别」（HH:mm），秒/毫秒忽略
        val nowMinutes = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
        val targetMinutes = hour * 60 + minute
        if (targetMinutes <= nowMinutes) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    /**
     * 获取距离响铃的剩余时间（毫秒）
     */
    suspend fun getTimeUntilAlarm(): Long {
        val state = dataStore.alarmState.first()
        if (state != AlarmState.ACTIVE) return 0L

        val triggerTime = dataStore.alarmTriggerTime.first()
        val now = System.currentTimeMillis()
        return if (triggerTime > now) triggerTime - now else 0L
    }

    /**
     * 根据指定的小时和分钟计算距离响铃的剩余时间（毫秒）
     * 计算逻辑和 calculateNextTriggerTime 保持一致（分钟粒度比较）
     */
    fun calculateTimeUntilAlarm(hour: Int, minute: Int): Long {
        val triggerTime = calculateNextTriggerTime(hour, minute)
        val now = System.currentTimeMillis()
        return if (triggerTime > now) triggerTime - now else 0L
    }

    /**
     * 检查是否可以调度精确闹钟
     * Android 12+ (API 31) 需要检查权限
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (e: Exception) {
                false
            }
        } else {
            // 低版本默认支持
            true
        }
    }

    /**
     * 开机后恢复闹钟（如果状态为 ACTIVE）
     */
    suspend fun restoreAlarmIfNeeded() {
        val state = dataStore.alarmState.first()
        AppLogger.d(TAG, "restoreAlarmIfNeeded: DataStore.state=$state")
        if (state == AlarmState.ACTIVE) {
            val hour = dataStore.alarmHour.first()
            val minute = dataStore.alarmMinute.first()
            val triggerTime = dataStore.alarmTriggerTime.first()
            val now = System.currentTimeMillis()
            AppLogger.d(TAG, "restoreAlarmIfNeeded: ACTIVE状态 hour=$hour, minute=$minute, triggerTime=$triggerTime, 剩余${(triggerTime - now) / 1000}秒")

            // v53：触发时间刚过不代表闹钟无效，可能是国产ROM漏投递广播/应用刚更新恢复。
            // 10分钟响铃窗口内必须立即补触发；只有超过窗口才清理。
            if (triggerTime <= now) {
                val overdueMs = now - triggerTime
                if (overdueMs <= MISSED_ALARM_RECOVERY_WINDOW_MS) {
                    AppLogger.eventW(TAG,
                        "restoreAlarmIfNeeded" to "MISSED_ALARM_RECOVER_NOW",
                        "overdue_ms" to overdueMs,
                        "triggerTime" to triggerTime)
                    val recoveryIntent = Intent(context, RingtoneService::class.java).apply {
                        action = RingtoneService.ACTION_START_RINGING
                        putExtra(EXTRA_ALARM_TRIGGER_TIME, triggerTime)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(recoveryIntent)
                    } else {
                        context.startService(recoveryIntent)
                    }
                } else {
                    AppLogger.w(TAG, "restoreAlarmIfNeeded: 已超过10分钟补响窗口（overdue=${overdueMs}ms），清理残留")
                    dataStore.cleanUpRingingState()
                }
                return
            }

            // 重新设置闹钟
            val result = setAlarm(hour, minute)
            if (result == SET_ALARM_FAILED) {
                // 权限缺失导致重启后无法重新设置闹钟，清理为未设置，下次打开弹权限引导
                AppLogger.e(TAG, "restoreAlarmIfNeeded: 重启恢复闹钟失败（SET_ALARM_FAILED），清为NOT_SET")
                dataStore.cleanUpRingingState()
            } else {
                AppLogger.d(TAG, "restoreAlarmIfNeeded: 重启恢复闹钟成功")
            }
        }
    }
}
