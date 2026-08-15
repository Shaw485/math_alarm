package com.mira.mathalarm.util

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import com.mira.mathalarm.data.AlarmDataStore
import com.mira.mathalarm.data.RingtoneOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val TAG_PREFIX = "MathAlarm"
    private val loggerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logFile: File? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val existingLatest = dir.listFiles()
                ?.filter { it.extension == "log" && it.isFile }
                ?.maxByOrNull { it.lastModified() }

            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val header = buildString {
                append("==== MathAlarm ${if (existingLatest != null && existingLatest.length() < 5 * 1024 * 1024) "重启（继承原日志文件）" else "启动"} $nowStr ====\n")
                append("App 包名: ${context.packageName}\n")
                append("Android 版本: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                append("设备型号: ${Build.BRAND} ${Build.MODEL} (产品=${Build.PRODUCT},硬件=${Build.HARDWARE})\n")
                append("制造商: ${Build.MANUFACTURER} | 主板: ${Build.BOARD} | 指纹: ${Build.FINGERPRINT.take(72)}...\n")
                runCatching {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    append("开机时长: ${android.os.SystemClock.elapsedRealtime() / 1000 / 60}分钟 | 屏亮:${pm.isInteractive} | Doze:${pm.isDeviceIdleMode} | 锁屏:${km.isKeyguardLocked} | 勿扰:${nm.currentInterruptionFilter} | 响铃模式:${ringerModeStr(am.ringerMode)}\n")
                    append("ALARM流音量:${am.getStreamVolume(AudioManager.STREAM_ALARM)}/${am.getStreamMaxVolume(AudioManager.STREAM_ALARM)} ")
                    append("| MUSIC流音量:${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} ")
                    append("| 通知音量:${am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)}/${am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)}\n")
                }
                append("================================================================\n\n")
            }

            logFile = if (existingLatest != null && existingLatest.length() < 5 * 1024 * 1024) {
                existingLatest.apply { appendText("\n\n$header") }
            } else {
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                File(dir, "mathalarm_${ts}.log").apply {
                    if (!exists()) createNewFile()
                    appendText(header)
                }
            }
        }.onFailure { e ->
            Log.e("${TAG_PREFIX}_Logger", "日志文件初始化失败", e)
        }
    }

    // =======================================================
    //  1. 基础 d/i/w/e + 新增 event(tag, Map<k,v>) 一条日志全字段
    // =======================================================
    private fun writeLine(priority: Char, tag: String, message: String, throwable: Throwable? = null) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val line = "$time $priority/${tag}: $message${throwable?.let { t ->
            "\n" + Log.getStackTraceString(t)
        }.orEmpty()}\n"

        when (priority) {
            'D' -> Log.d(tag, message, throwable)
            'I' -> Log.i(tag, message, throwable)
            'W' -> Log.w(tag, message, throwable)
            'E' -> Log.e(tag, message, throwable)
        }

        loggerScope.launch { runCatching { logFile?.appendText(line) } }
    }

    fun d(tag: String, message: String) = writeLine('D', tag, message)
    fun i(tag: String, message: String) = writeLine('I', tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = writeLine('W', tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = writeLine('E', tag, message, throwable)

    /** 一次性把多个 KV 字段打成一行 tab 分隔，便于 grep */
    fun event(tag: String, vararg pairs: Pair<String, Any?>) {
        val body = pairs.joinToString(separator = "  ") { (k, v) ->
            "$k=${fmtVal(v)}"
        }
        writeLine('I', tag, "[EVENT] $body")
    }

    fun eventW(tag: String, vararg pairs: Pair<String, Any?>) {
        val body = pairs.joinToString(separator = "  ") { (k, v) -> "$k=${fmtVal(v)}" }
        writeLine('W', tag, "[WARN-EVENT] $body")
    }

    private fun fmtVal(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> if (v) "Y" else "N"
        is Long, is Int, is Float, is Double, is Short, is Byte -> v.toString()
        is CharSequence -> v.toString().ifBlank { "<空串>" }.replace("\n", "\\n").take(160)
        is Enum<*> -> v.name
        is Class<*> -> v.simpleName
        is Iterable<*> -> v.joinToString(",", "[", "]", 8, "...") { fmtVal(it) }
        is Array<*> -> v.joinToString(",", "[", "]", 8, "...") { fmtVal(it) }
        else -> v.toString().replace("\n", "\\n").take(160)
    }

    // =======================================================
    //  2. dump Intent：action / component / flags / extras 全量
    // =======================================================
    fun dumpIntent(tag: String, prefix: String, intent: Intent?) {
        if (intent == null) { d(tag, "$prefix intent=null"); return }
        val flagsHex = "0x${intent.flags.toString(16)}"
        val flagsNames = parseIntentFlags(intent.flags)
        val cmp = intent.component?.flattenToShortString() ?: "null"
        val act = intent.action ?: "null"
        val dataStr = intent.dataString?.let { " data=[$it]" } ?: ""
        val cls = intent.`package`?.let { " pkg=[$it]" } ?: ""
        val extrasStr = formatBundle(intent.extras)
        d(tag, "$prefix Intent[action=$act, cmp=$cmp$dataStr$cls, flags=$flagsHex=[$flagsNames], extras=$extrasStr]")
    }

    private fun parseIntentFlags(flags: Int): String {
        val list = mutableListOf<String>()
        fun c(v: Int, n: String) { if (flags and v == v) list.add(n) }
        c(Intent.FLAG_ACTIVITY_NEW_TASK, "NEW_TASK")
        c(Intent.FLAG_ACTIVITY_CLEAR_TOP, "CLEAR_TOP")
        c(Intent.FLAG_ACTIVITY_SINGLE_TOP, "SINGLE_TOP")
        c(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT, "REORDER_TO_FRONT")
        c(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED, "RESET_TASK_IF_NEEDED")
        c(Intent.FLAG_ACTIVITY_CLEAR_TASK, "CLEAR_TASK")
        c(Intent.FLAG_ACTIVITY_NEW_DOCUMENT, "NEW_DOCUMENT")
        c(Intent.FLAG_ACTIVITY_MULTIPLE_TASK, "MULTIPLE_TASK")
        c(Intent.FLAG_ACTIVITY_NO_HISTORY, "NO_HISTORY")
        c(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS, "EXCLUDE_FROM_RECENTS")
        c(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT, "LAUNCH_ADJACENT")
        c(Intent.FLAG_GRANT_READ_URI_PERMISSION, "GRANT_READ_URI")
        c(Intent.FLAG_FROM_BACKGROUND, "FROM_BG")
        c(Intent.FLAG_RECEIVER_FOREGROUND, "RECEIVER_FG")
        c(Intent.FLAG_ACTIVITY_NO_USER_ACTION, "NO_USER_ACTION")
        return list.joinToString("|").ifBlank { "none" }
    }

    private fun formatBundle(b: Bundle?): String {
        if (b == null || b.isEmpty) return "{无}"
        val keys = b.keySet().toList().sorted()
        return keys.joinToString(",", "{", "}") { k ->
            val v = runCatching { b.get(k) }.getOrNull()
            val cls = v?.let { it::class.java.simpleName } ?: "Null"
            val value = when (v) {
                null -> "null"
                is Long, is Int, is Short, is Byte, is Boolean, is Float, is Double, is Char -> v.toString()
                is CharSequence -> "\"$v\""
                is java.io.Serializable -> v.toString().take(48).replace("\n", " ")
                else -> "${v.javaClass.simpleName}(hash=${v.hashCode()})"
            }
            "$k=$cls:$value"
        }
    }

    // =======================================================
    //  3. dump AudioStats：STREAM_ALARM/MUSIC/NOTIFICATION/RING 音量 + 响铃模式 + 麦克风/外放状态
    // =======================================================
    fun dumpAudioStats(tag: String, prefix: String, context: Context) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val alarmCur = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val musicCur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val musicMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val ringCur = am.getStreamVolume(AudioManager.STREAM_RING)
            val ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            val notifCur = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            val notifMax = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            val ringerMode = ringerModeStr(am.ringerMode)
            val speakerOn = runCatching { am.isSpeakerphoneOn }.getOrDefault(false)
            val micMute = runCatching { am.isMicrophoneMute }.getOrDefault(false)
            val musicActive = runCatching { am.isMusicActive }.getOrDefault(false)
            val hifi = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER).take(16) else "<P以下不支持"
            }.getOrDefault("<未知>")
            event(
                tag,
                "${prefix}_ALARM" to "${alarmCur}/${alarmMax}",
                "${prefix}_MUSIC" to "${musicCur}/${musicMax}",
                "${prefix}_RING" to "${ringCur}/${ringMax}",
                "${prefix}_NOTIF" to "${notifCur}/${notifMax}",
                "${prefix}_ringerMode" to ringerMode,
                "${prefix}_speaker" to speakerOn,
                "${prefix}_micMute" to micMute,
                "${prefix}_musicActive" to musicActive,
                "${prefix}_bufSize" to hifi
            )
        }.onFailure { w(tag, "$prefix dumpAudioStats 异常", it) }
    }

    private fun ringerModeStr(m: Int): String = when (m) {
        AudioManager.RINGER_MODE_SILENT -> "SILENT(静音)"
        AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE(仅震动)"
        AudioManager.RINGER_MODE_NORMAL -> "NORMAL(响铃)"
        else -> "UNKNOWN($m)"
    }

    // =======================================================
    //  4. dump WindowManager.LayoutParams：type 解释 + flags 解码 + brightness/alpha/cutoutMode
    // =======================================================
    fun dumpWmParams(tag: String, prefix: String, p: WindowManager.LayoutParams, composeView: android.view.View? = null) {
        val typeName = when (p.type) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY -> "TYPE_APPLICATION_OVERLAY(悬浮窗API26+)"
            WindowManager.LayoutParams.TYPE_PHONE -> "TYPE_PHONE(悬浮窗旧API<26)"
            WindowManager.LayoutParams.TYPE_APPLICATION -> "TYPE_APPLICATION"
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG -> "TYPE_APPLICATION_ATTACHED_DIALOG"
            2038 -> "TYPE_APPLICATION_OVERLAY(2038=0x7F6)"
            else -> "TYPE_UNKNOWN(${p.type})"
        }
        val flagsDecoded = parseWmFlags(p.flags)
        val brightness = if (p.screenBrightness < 0f) "follow-system(${p.screenBrightness})" else String.format(Locale.US, "%.3f", p.screenBrightness)
        val alpha = String.format(Locale.US, "%.3f", p.alpha)
        val sizeLine = "w=${p.width} h=${p.height} gravity=(${gravityStr(p.gravity)}) x=${p.x} y=${p.y}"
        val extra = if (composeView != null) {
            " | View[attached=${composeView.isAttachedToWindow}, shown=${composeView.isShown}, focused=${composeView.hasWindowFocus()}, mW=${composeView.measuredWidth}, mH=${composeView.measuredHeight}, w=${composeView.width}, h=${composeView.height}]"
        } else ""
        d(tag, "$prefix WM[$typeName, $sizeLine, flags=0x${p.flags.toString(16)}=[$flagsDecoded], bright=$brightness, alpha=$alpha]$extra")
    }

    private fun parseWmFlags(f: Int): String {
        val ls = mutableListOf<String>()
        fun c(v: Int, n: String) { if (f and v == v) ls.add(n) }
        c(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, "SHOW_WHEN_LOCKED")
        c(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD, "DISMISS_KEYGUARD")
        c(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON, "TURN_SCREEN_ON")
        c(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, "KEEP_SCREEN_ON")
        c(WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON, "ALLOW_LOCK_WHILE_SCREEN_ON")
        c(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, "LAYOUT_IN_SCREEN")
        c(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, "LAYOUT_NO_LIMITS")
        c(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, "HARDWARE_ACCEL")
        c(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, "ALT_FOCUSABLE_IM")
        c(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, "NOT_FOCUSABLE")
        c(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, "NOT_TOUCHABLE")
        c(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, "NOT_TOUCH_MODAL")
        c(WindowManager.LayoutParams.FLAG_SECURE, "SECURE")
        c(WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR, "LAYOUT_INSET_DECOR")
        c(WindowManager.LayoutParams.FLAG_FULLSCREEN, "FULLSCREEN")
        return ls.joinToString("|").ifBlank { "none" }
    }

    private fun gravityStr(g: Int): String {
        val ls = mutableListOf<String>()
        fun c(v: Int, n: String) { if (g and v == v) ls.add(n) }
        c(Gravity.TOP(), "TOP"); c(Gravity.BOTTOM(), "BOTTOM"); c(Gravity.LEFT(), "LEFT")
        c(Gravity.RIGHT(), "RIGHT"); c(Gravity.CENTER_VERTICAL(), "CENTER_V"); c(Gravity.CENTER_HORIZONTAL(), "CENTER_H")
        c(Gravity.START(), "START"); c(Gravity.END(), "END")
        return ls.joinToString("+").ifBlank { "0x${g.toString(16)}" }
    }

    private object Gravity {  // 包一层省 import android.view.Gravity 冲突
        fun TOP(): Int = android.view.Gravity.TOP
        fun BOTTOM(): Int = android.view.Gravity.BOTTOM
        fun LEFT(): Int = android.view.Gravity.LEFT
        fun RIGHT(): Int = android.view.Gravity.RIGHT
        fun CENTER_VERTICAL(): Int = android.view.Gravity.CENTER_VERTICAL
        fun CENTER_HORIZONTAL(): Int = android.view.Gravity.CENTER_HORIZONTAL
        fun START(): Int = android.view.Gravity.START
        fun END(): Int = android.view.Gravity.END
    }

    // =======================================================
    //  5. dump Screen/WakeLock/Keyguard/Doze 状态
    // =======================================================
    fun dumpWakeLockScreen(tag: String, prefix: String, context: Context, extraWl: PowerManager.WakeLock? = null) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val interruption = when (nm.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_ALL -> "ALL(无勿扰)"
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "NONE(全勿扰)"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS_ONLY"
                else -> "UNKNOWN(${nm.currentInterruptionFilter})"
            }
            val dndPol = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm.consolidatedNotificationPolicy?.let {
                    "prioCategories=0x${it.priorityCategories.toString(16)}"
                } ?: "<null>" else "<M以下不支持"
            }.getOrDefault("<未知>")
            event(
                tag,
                "${prefix}_isInteractive(屏亮)" to pm.isInteractive,
                "${prefix}_isPowerSave" to runCatching { pm.isPowerSaveMode }.getOrDefault(false),
                "${prefix}_isDeviceIdle(Doze)" to runCatching { pm.isDeviceIdleMode }.getOrDefault(false),
                "${prefix}_isIgnoringBatteryOpt" to runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pm.isIgnoringBatteryOptimizations(context.packageName) else true
                }.getOrDefault(false),
                "${prefix}_isKeyguardLocked" to km.isKeyguardLocked,
                "${prefix}_isKeyguardSecure" to runCatching { km.isKeyguardSecure }.getOrDefault(false),
                "${prefix}_isDeviceSecure" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) km.isDeviceSecure else false,
                "${prefix}_inCall" to runCatching { (context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager).callState != 0 }.getOrDefault(false),
                "${prefix}_dndFilter" to interruption,
                "${prefix}_dndPolicy" to dndPol
            )
            extraWl?.let { wl -> event(tag, "${prefix}_extraWl" to "isHeld=${wl.isHeld}, hashCode=${wl.hashCode()}") }
        }.onFailure { w(tag, "$prefix dumpWakeLockScreen 异常", it) }
    }

    // =======================================================
    //  6. dump DataStore 全量字段（9 个关键 KV 一次看全）
    // =======================================================
    fun dumpDataStoreAll(tag: String, prefix: String, dataStore: AlarmDataStore, triggerHumanT: Boolean = true) {
        runBlocking {
            try {
                val st = dataStore.alarmState.first()
                val at = dataStore.alarmTriggerTime.first()
                val ir = dataStore.isRinging.first()
                val ah = dataStore.alarmHour.first()
                val am = dataStore.alarmMinute.first()
                val lh = dataStore.lastHour.first()
                val lm = dataStore.lastMinute.first()
                val rn = dataStore.ringtoneOption.first()
                val showPerm = dataStore.hasShownPermissionGuide.first()
                val onboard = dataStore.hasSeenOnboarding.first()

                val trigText = if (triggerHumanT && at > 0L) {
                    val df = SimpleDateFormat("HH:mm:ss(MM-dd)", Locale.getDefault())
                    val cur = System.currentTimeMillis()
                    val diffSec = (at - cur) / 1000L
                    "${df.format(Date(at))} diff=${diffHuman(diffSec)}"
                } else at.toString()

                event(
                    tag,
                    "${prefix}_state" to st.name,
                    "${prefix}_triggerT" to trigText,
                    "${prefix}_isRinging" to ir,
                    "${prefix}_alarmH" to ah,
                    "${prefix}_alarmM" to am,
                    "${prefix}_lastH" to lh,
                    "${prefix}_lastM" to lm,
                    "${prefix}_ringtone" to "${rn.displayName}(${rn.volumeScale})",
                    "${prefix}_resId" to rn.resId,
                    "${prefix}_hasShownPermGuide" to showPerm,
                    "${prefix}_hasSeenOnboarding" to onboard
                )
            } catch (t: Throwable) {
                w(tag, "$prefix dumpDataStoreAll 异常", t)
            }
        }
    }

    private fun diffHuman(sec: Long): String = when {
        sec >= 0 -> "${sec / 3600}h${(sec % 3600) / 60}m${sec % 60}s后响"
        else -> {
            val past = -sec
            "${past / 3600}h${(past % 3600) / 60}m${past % 60}s前已过"
        }
    }

    /** 返回最新的日志文件，用于导出分享 */
    fun getLatestLogFile(): File? = logFile?.takeIf { it.exists() }

    // =======================================================
    //  7. RingtoneOption 扩展 dump（MediaPlayer 属性一起打）
    // =======================================================
    fun dumpRingtoneAndPlayer(tag: String, prefix: String, opt: RingtoneOption, mp: android.media.MediaPlayer? = null) {
        event(tag,
            "${prefix}_name" to opt.displayName,
            "${prefix}_resId" to "0x${opt.resId.toString(16)}",
            "${prefix}_volScale" to opt.volumeScale,
            "${prefix}_playerExists" to (mp != null)
        )
        mp?.let { p ->
            runCatching {
                eventW(tag,
                    "${prefix}_player_isPlaying" to p.isPlaying,
                    "${prefix}_player_isLooping" to p.isLooping,
                    "${prefix}_player_curPos_ms" to p.currentPosition,
                    "${prefix}_player_dur_ms" to (runCatching { p.duration }.getOrNull() ?: -1),
                    "${prefix}_player_audioSessionId" to p.audioSessionId
                )
            }.onFailure { w(tag, "${prefix}_player dump 异常", it) }
        }
    }
}
