package com.mira.mathalarm.permission

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 权限检查工具类
 * 统一管理通知、精确闹钟、全屏通知、电池优化四类权限的检查与跳转
 * 按系统版本做条件判断，对应 PRD 5.14 权限的系统版本适配规则
 *
 * 新增第 5 类「后台保活 & 自启动」：
 *   - 国产 ROM（小米/Huawei/OPPO/vivo/荣耀等）无法程序化检测是否真的开了自启动
 *   - 只要用户**点击「去授权」成功跳转到对应 ROM 的设置页**，就持久化 hasCompletedAutostartGuide=true
 *   - 下次 ON_RESUME 回 App 视为该步已完成，避免无限循环要授权
 *   - 用户可随时手动在权限检查对话框里看到该项状态
 */
object PermissionChecker {

    private const val PREFS_NAME = "mathalarm_permission_prefs"
    private const val KEY_AUTOSTART_GUIDE_COMPLETED = "has_completed_autostart_guide"

    /** 自启动引导：用户是否已经至少点击过一次「去授权」跳转（国产 ROM 无法程序化判断） */
    fun hasCompletedAutostartGuide(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART_GUIDE_COMPLETED, false)
    }

    /** 标记自启动引导已完成（用户点了「去授权」后返回 App 时调用） */
    fun markAutostartGuideCompleted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTOSTART_GUIDE_COMPLETED, true)
            .apply()
    }

    /**
     * 获取「后台保活 & 自启动」设置页 Intent
     * - 小米/红米/POCO HyperOS/MIUI: 跳 miui.intent.action.OP_AUTO_START 专属页（最精准）
     * - 其他 ROM: 跳应用详情页，用户在里面找"自启动"/"后台活动"/"电池无限制"
     */
    fun getAutostartSettingsIntent(context: Context): Intent {
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        // 小米系列（小米/红米/POCO 全部用这个 intent）
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            val miuiIntent = Intent("miui.intent.action.OP_AUTO_START").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (miuiIntent.resolveActivity(context.packageManager) != null) {
                return miuiIntent
            }
        }
        // 华为/荣耀
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            try {
                val hwIntent = Intent().apply {
                    component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (hwIntent.resolveActivity(context.packageManager) != null) return hwIntent
            } catch (_: Exception) {}
        }
        // OPPO/一加/realme
        if (manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme")) {
            try {
                val oppoIntent = Intent().apply {
                    component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (oppoIntent.resolveActivity(context.packageManager) != null) return oppoIntent
            } catch (_: Exception) {}
        }
        // vivo/iQOO
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            try {
                val vivoIntent = Intent().apply {
                    component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (vivoIntent.resolveActivity(context.packageManager) != null) return vivoIntent
            } catch (_: Exception) {}
        }
        // 兜底：跳应用详情页，用户手动在"电池"/"自启动"栏目设置
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * 检查通知权限是否已授予
     * Android 13+ (API 33) 需要运行时请求，低版本默认已授予
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }

    /**
     * 检查精确闹钟权限是否已授予
     * Android 12+ (API 31) 需要检查，低版本默认支持
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            try {
                alarmManager.canScheduleExactAlarms()
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }
    }

    /**
     * 检查全屏通知权限是否已授予
     * Android 14+ (API 34) 需要运行时授予，低版本清单声明即可
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }
    }

    /**
     * 检查电池优化是否已忽略（即 App 加入白名单）
     * Android 6.0+ (API 23) 引入电池优化 Doze 模式
     * 未忽略则 App 在深度休眠时可能被系统杀掉，导致闹钟不准时
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * 检查是否已授予「显示悬浮窗 / 后台弹出界面」权限（SYSTEM_ALERT_WINDOW）
     * 国产ROM杀App后startActivity被静默拦截时，这是WindowManager TYPE_APPLICATION_OVERLAY
     * 全屏悬浮答题页能够工作的唯一官方支持前提；权限引导第5步强制授权
     * Android 6.0+ (API 23) 程序化检查，低版本自动已授予
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Settings.canDrawOverlays(context)
            } catch (e: Exception) {
                // 部分国产ROM Settings Provider 查询异常时保守返回 false，让用户重走授权页验证
                false
            }
        } else {
            true
        }
    }

    /**
     * 检查是否所有必需权限都已授予（v48新增第5项canDrawOverlays）
     */
    fun hasAllPermissions(context: Context): Boolean {
        return areNotificationsEnabled(context) &&
                canScheduleExactAlarms(context) &&
                canUseFullScreenIntent(context) &&
                isIgnoringBatteryOptimizations(context) &&
                canDrawOverlays(context)
    }

    /**
     * 获取缺失的权限列表（用于展示风险提示，v48新增第5项悬浮窗）
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!areNotificationsEnabled(context)) missing.add("通知")
        if (!canScheduleExactAlarms(context)) missing.add("精确闹钟")
        if (!canUseFullScreenIntent(context)) missing.add("全屏通知")
        if (!isIgnoringBatteryOptimizations(context)) missing.add("电池优化")
        if (!canDrawOverlays(context)) missing.add("悬浮窗")
        return missing
    }

    /**
     * 获取精确闹钟设置页面 Intent
     */
    fun getExactAlarmSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            null
        }
    }

    /**
     * 获取全屏通知设置页面 Intent
     */
    fun getFullScreenIntentSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            null
        }
    }

    /**
     * 获取忽略电池优化设置页面 Intent
     * 直接弹出系统对话框：要将 xxx 添加到未受优化的应用列表吗？
     */
    fun getIgnoreBatteryOptimizationsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }

    /**
     * 获取「显示悬浮窗 / 后台弹出界面」权限设置页面 Intent
     * - Android 6.0+ (API 23) 官方 Intent: Settings.ACTION_MANAGE_OVERLAY_PERMISSION + package Uri
     * - 小米 HyperOS/MIUI 用这个Intent会直接精准跳到「数学闹钟」的单项悬浮窗开关页
     * - 用户开了悬浮窗才能用 WindowManager TYPE_APPLICATION_OVERLAY 终极兜底答题页
     *   （国产ROM杀App后startActivity被静默拦截时的最后防线）
     */
    fun getOverlayPermissionIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        }
    }

    /**
     * 获取应用通知设置页面 Intent
     */
    fun getNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    /**
     * 获取应用详情设置页面 Intent（兜底跳转）
     */
    fun getAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    /**
     * 是否需要申请通知权限（运行时申请）
     */
    fun needRequestNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    /**
     * 是否需要申请精确闹钟权限（跳系统页）
     */
    fun needRequestExactAlarmPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * 是否需要申请全屏通知权限（跳系统页）
     */
    fun needRequestFullScreenIntentPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }

    /**
     * 是否需要申请忽略电池优化权限（直接弹系统对话框）
     */
    fun needRequestIgnoreBatteryOptimizations(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
