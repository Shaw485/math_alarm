package com.mira.mathalarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mira.mathalarm.util.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Context 扩展属性，全局唯一 DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "math_alarm_prefs")

/**
 * 闹钟数据存储管理器
 * 使用 DataStore 进行本地键值存储，管理闹钟时间、状态、铃声、引导标记等
 */
class AlarmDataStore(private val context: Context) {

    companion object {
        private const val TAG = "AlarmDataStore"
        // 闹钟小时（0-23）
        private val KEY_ALARM_HOUR = intPreferencesKey("alarm_hour")
        // 闹钟分钟（0-59）
        private val KEY_ALARM_MINUTE = intPreferencesKey("alarm_minute")
        // 闹钟状态（AlarmState 枚举 ordinal）
        private val KEY_ALARM_STATE = intPreferencesKey("alarm_state")
        // 闹钟触发时间戳（毫秒），用于校验是否为同一轮闹钟
        private val KEY_ALARM_TRIGGER_TIME = longPreferencesKey("alarm_trigger_time")
        // 铃声选项（RingtoneOption 枚举 ordinal）
        private val KEY_RINGTONE_OPTION = intPreferencesKey("ringtone_option")
        // 最近一次选择的小时（未确认也保留）
        private val KEY_LAST_HOUR = intPreferencesKey("last_hour")
        // 最近一次选择的分钟
        private val KEY_LAST_MINUTE = intPreferencesKey("last_minute")
        // 是否已展示首次启动引导
        private val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        // 是否已展示权限引导（仅记录是否弹过，不替代权限状态检查）
        private val KEY_HAS_SHOWN_PERMISSION_GUIDE = booleanPreferencesKey("has_shown_permission_guide")
        // 响铃中标记（进程被杀后恢复判断用）
        private val KEY_IS_RINGING = booleanPreferencesKey("is_ringing")
    }

    // 默认闹钟时间：09:32（与 PRD 一致）
    private val defaultHour = 9
    private val defaultMinute = 32

    /** 闹钟小时 Flow */
    val alarmHour: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_ALARM_HOUR] ?: defaultHour
        }

    /** 闹钟分钟 Flow */
    val alarmMinute: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_ALARM_MINUTE] ?: defaultMinute
        }

    /** 闹钟状态 Flow */
    val alarmState: Flow<AlarmState> = context.dataStore.data
        .map { preferences ->
            val ordinal = preferences[KEY_ALARM_STATE] ?: AlarmState.NOT_SET.ordinal
            AlarmState.values().getOrNull(ordinal) ?: AlarmState.NOT_SET
        }

    /** 闹钟触发时间戳 Flow */
    val alarmTriggerTime: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_ALARM_TRIGGER_TIME] ?: 0L
        }

    /** 铃声选项 Flow */
    val ringtoneOption: Flow<RingtoneOption> = context.dataStore.data
        .map { preferences ->
            val ordinal = preferences[KEY_RINGTONE_OPTION] ?: RingtoneOption.QINGCHEN.ordinal
            RingtoneOption.fromOrdinal(ordinal)
        }

    /** 最近一次选择的小时 Flow */
    val lastHour: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_LAST_HOUR] ?: defaultHour
        }

    /** 最近一次选择的分钟 Flow */
    val lastMinute: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_LAST_MINUTE] ?: defaultMinute
        }

    /** 是否已展示首次启动引导 Flow */
    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_HAS_SEEN_ONBOARDING] ?: false
        }

    /** 是否已展示过权限引导 Flow（避免每次打开都弹） */
    val hasShownPermissionGuide: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_HAS_SHOWN_PERMISSION_GUIDE] ?: false
        }

    /** 是否处于响铃中 Flow */
    val isRinging: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_IS_RINGING] ?: false
        }

    /**
     * 保存闹钟设置（时间 + 状态 + 触发时间戳）
     */
    suspend fun saveAlarm(hour: Int, minute: Int, triggerTime: Long) {
        val ts0 = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            preferences[KEY_ALARM_HOUR] = hour
            preferences[KEY_ALARM_MINUTE] = minute
            preferences[KEY_ALARM_STATE] = AlarmState.ACTIVE.ordinal
            preferences[KEY_ALARM_TRIGGER_TIME] = triggerTime
            preferences[KEY_LAST_HOUR] = hour
            preferences[KEY_LAST_MINUTE] = minute
        }
        val trigHuman = run {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss(MM-dd)", java.util.Locale.getDefault())
            val ds = (triggerTime - System.currentTimeMillis()) / 1000L
            "${sdf.format(java.util.Date(triggerTime))} diff=${if (ds >= 0) "${ds / 3600}h${(ds % 3600) / 60}m${ds % 60}s后响" else "${(-ds) / 3600}h${((-ds) % 3600) / 60}m${(-ds) % 60}s前已过"}"
        }
        AppLogger.eventW(TAG,
            "saveAlarm" to "OK",
            "hour" to hour,
            "minute" to minute,
            "state→" to AlarmState.ACTIVE.name,
            "triggerHuman" to trigHuman,
            "triggerMillis" to triggerTime,
            "lastH" to hour,
            "lastM" to minute,
            "editTook_ms" to (System.currentTimeMillis() - ts0))
    }

    /**
     * 更新闹钟状态
     */
    suspend fun updateAlarmState(state: AlarmState) {
        val ts0 = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            preferences[KEY_ALARM_STATE] = state.ordinal
        }
        AppLogger.event(TAG,
            "updateAlarmState" to "OK",
            "newOrdinal" to state.ordinal,
            "newName" to state.name,
            "editTook_ms" to (System.currentTimeMillis() - ts0))
    }

    /**
     * 保存铃声选项
     */
    suspend fun saveRingtoneOption(option: RingtoneOption) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RINGTONE_OPTION] = option.ordinal
        }
        AppLogger.event(TAG,
            "saveRingtoneOption" to "OK",
            "displayName" to option.displayName,
            "ordinal" to option.ordinal,
            "volumeScale" to option.volumeScale,
            "resId" to "0x${option.resId.toString(16)}")
    }

    /**
     * 保存最近选择的时间（未确认也保留）
     */
    suspend fun saveLastTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_HOUR] = hour
            preferences[KEY_LAST_MINUTE] = minute
        }
        AppLogger.d(TAG, "saveLastTime: lastH=$hour, lastM=$minute")
    }

    /**
     * 标记已展示首次启动引导
     */
    suspend fun setHasSeenOnboarding(seen: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HAS_SEEN_ONBOARDING] = seen
        }
        AppLogger.d(TAG, "setHasSeenOnboarding: seen=$seen")
    }

    /**
     * 标记已展示权限引导（展示过一次后不再自动弹，只保留底部警告条）
     */
    suspend fun setHasShownPermissionGuide(shown: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HAS_SHOWN_PERMISSION_GUIDE] = shown
        }
        AppLogger.d(TAG, "setHasShownPermissionGuide: shown=$shown")
    }

    /**
     * 设置响铃中标记
     */
    suspend fun setIsRinging(ringing: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_RINGING] = ringing
        }
        AppLogger.event(TAG, "setIsRinging" to "OK", "isRinging" to ringing)
    }

    /**
     * 清除闹钟（回到未设置状态）——Bug2 修复：清状态前统一强制停铃+关通知
     * 任何场景调用 clearAlarm 都会做三件事：停 RingtoneService、取消(1001/1002)通知、清答题页标记
     */
    suspend fun clearAlarm() {
        val ts0 = System.currentTimeMillis()
        AppLogger.eventW(TAG, "clearAlarm" to "START", "reason" to "DataStore.clearAlarm 被调用（状态回NOT_SET+forceStop三件套）")
        AppLogger.dumpDataStoreAll(TAG, "clearAlarm-前", this)

        // ✅ 先强停铃和通知（无论当前是否真的在响，都无副作用）
        com.mira.mathalarm.service.RingtoneService.forceStopRingingAndNotify(context)

        context.dataStore.edit { preferences ->
            preferences[KEY_ALARM_STATE] = AlarmState.NOT_SET.ordinal
            preferences[KEY_ALARM_TRIGGER_TIME] = 0L
            preferences[KEY_IS_RINGING] = false
        }
        AppLogger.dumpDataStoreAll(TAG, "clearAlarm-后", this)
        AppLogger.event(TAG,
            "clearAlarm" to "OK",
            "state→" to AlarmState.NOT_SET.name,
            "triggerT→" to "0L",
            "isRinging→" to false,
            "took_ms" to (System.currentTimeMillis() - ts0))
    }

    /**
     * 清理异常响铃中间态（异常终止恢复时调用）
     * 响铃结束后状态应为：本次闹钟已失效
     */
    suspend fun cleanUpRingingState() {
        val ts0 = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_RINGING] = false
            preferences[KEY_ALARM_STATE] = AlarmState.DISABLED.ordinal
        }
        AppLogger.event(TAG,
            "cleanUpRingingState" to "OK",
            "isRinging→" to false,
            "state→" to AlarmState.DISABLED.name,
            "took_ms" to (System.currentTimeMillis() - ts0))
    }
}
