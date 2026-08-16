package com.mira.mathalarm.ui.home

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mira.mathalarm.alarm.AlarmScheduler
import com.mira.mathalarm.data.AlarmDataStore
import com.mira.mathalarm.data.AlarmState
import com.mira.mathalarm.data.RingtoneOption
import com.mira.mathalarm.permission.PermissionChecker
import com.mira.mathalarm.service.RingtoneService
import com.mira.mathalarm.ui.ringing.RingingActivity
import com.mira.mathalarm.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 首页 ViewModel
 * 管理首页状态、闹钟操作、铃声选择等业务逻辑
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HomeViewModel"
        private const val MAX_VALID_RINGING_AGE_MS = 10 * 60 * 1000L + 30_000L
    }

    private val context = application.applicationContext
    private val dataStore = AlarmDataStore(context)
    private val alarmScheduler = AlarmScheduler(context)

    // UI 状态
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 倒计时刷新 Job
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            AppLogger.d(TAG, "ViewModel init: 开始从DataStore恢复持久化状态")
            val state = dataStore.alarmState.first()
            val hour = dataStore.alarmHour.first()
            val minute = dataStore.alarmMinute.first()
            val triggerTime = dataStore.alarmTriggerTime.first()
            val lastHour = dataStore.lastHour.first()
            val lastMinute = dataStore.lastMinute.first()
            val ringtoneOption = dataStore.ringtoneOption.first()
            val hasSeenOnboarding = dataStore.hasSeenOnboarding.first()
            val isRinging = dataStore.isRinging.first()
            val hasShownPermissionGuide = dataStore.hasShownPermissionGuide.first()
            AppLogger.d(TAG, "ViewModel init: state=$state, alarmH=$hour, alarmM=$minute, triggerT=$triggerTime, lastH=$lastHour, lastM=$lastMinute, ringtone=${ringtoneOption.displayName}, isRinging=$isRinging, hasShownPermGuide=$hasShownPermissionGuide, hasSeenOnboarding=$hasSeenOnboarding")

            // 启动时预先计算 ACTIVE 状态的 remaining，避免先显示 0 再跳变
            val initRemaining = if (state == AlarmState.ACTIVE && triggerTime > System.currentTimeMillis()) {
                triggerTime - System.currentTimeMillis()
            } else 0L

            _uiState.value = _uiState.value.copy(
                alarmState = state,
                alarmHour = hour,
                alarmMinute = minute,
                selectedHour = if (state == AlarmState.NOT_SET) lastHour else hour,
                selectedMinute = if (state == AlarmState.NOT_SET) lastMinute else minute,
                ringtoneOption = ringtoneOption,
                hasSeenOnboarding = hasSeenOnboarding,
                isRinging = isRinging,
                remainingTimeMillis = initRemaining
            )

            // v52：RINGING 不代表异常终止。国产 ROM 可能在答题 Overlay 刚挂载后重建进程，
            // 此时清状态会立即 stopService + 移除答题页，表现为“响一下就能关掉”。
            // 统一交给 refreshAlarmState() 按 triggerTime/isRinging 判断，并在需要时恢复 Service。
            if (isRinging || state == AlarmState.RINGING) {
                AppLogger.eventW(TAG,
                    "HomeVM-init-RINGING" to "DEFER_TO_REFRESH(禁止旧逻辑直接clearAlarm)",
                    "state" to state.name,
                    "isRinging" to isRinging,
                    "triggerTime" to triggerTime)
                refreshAlarmState()
            }

            // 启动倒计时刷新
            if (_uiState.value.alarmState == AlarmState.ACTIVE) {
                startCountdown()
            }

            // 如果是失效状态，3秒后自动回到未设置
            if (_uiState.value.alarmState == AlarmState.DISABLED) {
                delay(3000)
                if (_uiState.value.alarmState == AlarmState.DISABLED) {
                    _uiState.value = _uiState.value.copy(
                        alarmState = AlarmState.NOT_SET,
                        remainingTimeMillis = 0L
                    )
                    dataStore.clearAlarm()
                }
            }

            // 刷新权限状态：只要当前有未授权项就自动弹出引导框；
            // 全部授权后 hasAllPermissions=true，以后打开就不再弹（既不是每次都弹骚扰，也不是只弹一次）
            refreshPermissionStatus()
            if (!_uiState.value.hasAllPermissions) {
                _uiState.value = _uiState.value.copy(
                    showPermissionGuideDialog = true
                )
            }
        }
    }

    /**
     * 更新选中的小时
     */
    fun updateSelectedHour(hour: Int) {
        _uiState.value = _uiState.value.copy(selectedHour = hour)
        viewModelScope.launch {
            dataStore.saveLastTime(hour, _uiState.value.selectedMinute)
            if (_uiState.value.alarmState == AlarmState.ACTIVE && _uiState.value.isEditing) {
                val remaining = alarmScheduler.calculateTimeUntilAlarm(hour, _uiState.value.selectedMinute)
                _uiState.value = _uiState.value.copy(remainingTimeMillis = remaining)
            }
        }
    }

    /**
     * 更新选中的分钟
     */
    fun updateSelectedMinute(minute: Int) {
        _uiState.value = _uiState.value.copy(selectedMinute = minute)
        viewModelScope.launch {
            dataStore.saveLastTime(_uiState.value.selectedHour, minute)
            if (_uiState.value.alarmState == AlarmState.ACTIVE && _uiState.value.isEditing) {
                val remaining = alarmScheduler.calculateTimeUntilAlarm(_uiState.value.selectedHour, minute)
                _uiState.value = _uiState.value.copy(remainingTimeMillis = remaining)
            }
        }
    }

    /**
     * 确认设置闹钟
     */
    fun confirmSetAlarm() {
        viewModelScope.launch {
            val hour = _uiState.value.selectedHour
            val minute = _uiState.value.selectedMinute
            AppLogger.d(TAG, "confirmSetAlarm: 用户点确认设置，hour=$hour, minute=$minute, 之前状态=${_uiState.value.alarmState}, isEditing=${_uiState.value.isEditing}")
            val triggerTime = alarmScheduler.setAlarm(hour, minute)

            // ⚠️ 关键：setAlarm 返回 SET_ALARM_FAILED(-1) 表示没权限/异常
            if (triggerTime == AlarmScheduler.SET_ALARM_FAILED) {
                AppLogger.e(TAG, "confirmSetAlarm: setAlarm 返回 SET_ALARM_FAILED，无精确闹钟权限，弹权限引导，不修改状态")
                _uiState.value = _uiState.value.copy(
                    showPermissionGuideDialog = true
                )
                return@launch
            }

            // 立刻从 triggerTime 计算剩余时间写入 UI
            val remainingMillis = triggerTime - System.currentTimeMillis()
            AppLogger.d(TAG, "confirmSetAlarm: 设置成功，triggerTime=$triggerTime, 剩余时间=${remainingMillis / 1000}秒（${remainingMillis / 1000 / 60}分${(remainingMillis / 1000) % 60}秒）")

            _uiState.value = _uiState.value.copy(
                alarmState = AlarmState.ACTIVE,
                alarmHour = hour,
                alarmMinute = minute,
                remainingTimeMillis = remainingMillis,
                showSetSuccessToast = true,
                isEditing = false
            )

            // 立即启动倒计时循环（进入循环后先立刻刷新一次，然后每 60 秒刷新）
            startCountdown()
            AppLogger.d(TAG, "confirmSetAlarm: startCountdown 已启动")

            // 3 秒后仅隐藏 Toast，不影响其他逻辑
            delay(3000)
            _uiState.value = _uiState.value.copy(showSetSuccessToast = false)
        }
    }

    /**
     * 修改闹钟（进入编辑态）
     */
    fun enterEditMode() {
        val hour = _uiState.value.selectedHour
        val minute = _uiState.value.selectedMinute
        val remaining = alarmScheduler.calculateTimeUntilAlarm(hour, minute)
        AppLogger.w(TAG, "enterEditMode: 仅进入编辑态，尚未重新调度系统闹钟；hour=$hour, minute=$minute, 预计剩余=${remaining / 1000}秒")
        _uiState.value = _uiState.value.copy(
            isEditing = true,
            remainingTimeMillis = remaining,
            // 只有 confirmSetAlarm() 真正写入 AlarmManager/DataStore 后才能显示成功 Toast。
            // 旧逻辑在此显示「将在 X 后响铃」，会让用户误以为修改已保存。
            showSetSuccessToast = false
        )
    }

    /**
     * 失效闹钟
     */
    fun disableAlarm() {
        viewModelScope.launch {
            AppLogger.d(TAG, "disableAlarm: 用户点失效，将AlarmManager取消+状态DISABLED")
            alarmScheduler.disableAlarm()
            _uiState.value = _uiState.value.copy(
                alarmState = AlarmState.DISABLED,
                showDisableDialog = false
            )

            // 3 秒后自动回流到未设置状态
            delay(3000)
            if (_uiState.value.alarmState == AlarmState.DISABLED) {
                AppLogger.d(TAG, "disableAlarm: DISABLED停留3秒，回流NOT_SET")
                _uiState.value = _uiState.value.copy(
                    alarmState = AlarmState.NOT_SET
                )
            }
        }
    }

    /**
     * 显示失效确认弹窗
     */
    fun showDisableDialog() {
        _uiState.value = _uiState.value.copy(showDisableDialog = true)
    }

    /**
     * 隐藏失效确认弹窗
     */
    fun hideDisableDialog() {
        _uiState.value = _uiState.value.copy(showDisableDialog = false)
    }

    /**
     * 显示查看题目弹窗
     */
    fun showViewProblemDialog() {
        _uiState.value = _uiState.value.copy(showViewProblemDialog = true)
    }

    /**
     * 隐藏查看题目弹窗
     */
    fun hideViewProblemDialog() {
        _uiState.value = _uiState.value.copy(showViewProblemDialog = false)
    }

    /**
     * 显示铃声选择浮窗
     */
    fun showRingtonePicker() {
        _uiState.value = _uiState.value.copy(showRingtonePicker = true)
    }

    /**
     * 隐藏铃声选择浮窗
     */
    fun hideRingtonePicker() {
        _uiState.value = _uiState.value.copy(showRingtonePicker = false)
    }

    /**
     * 选择铃声
     */
    fun selectRingtone(option: RingtoneOption) {
        _uiState.value = _uiState.value.copy(ringtoneOption = option)
        viewModelScope.launch {
            dataStore.saveRingtoneOption(option)
        }
    }

    /**
     * 标记已展示首次启动引导
     */
    fun markOnboardingSeen() {
        _uiState.value = _uiState.value.copy(
            hasSeenOnboarding = true,
            showOnboardingDialog = false
        )
        viewModelScope.launch {
            dataStore.setHasSeenOnboarding(true)
        }
    }

    /**
     * 显示首次启动引导弹窗
     */
    fun showOnboardingDialog() {
        _uiState.value = _uiState.value.copy(showOnboardingDialog = true)
    }

    /**
     * 显示权限引导弹窗
     */
    fun showPermissionGuideDialog() {
        _uiState.value = _uiState.value.copy(showPermissionGuideDialog = true)
    }

    /**
     * 关闭权限引导弹窗
     */
    fun dismissPermissionGuide() {
        AppLogger.d(TAG, "dismissPermissionGuide: 用户关闭权限引导弹窗，标记已展示并刷新权限")
        _uiState.value = _uiState.value.copy(showPermissionGuideDialog = false)
        viewModelScope.launch {
            // 标记权限引导已展示过一次，以后不再自动弹，仅保留底部警告条
            dataStore.setHasShownPermissionGuide(true)
            // 刷新权限状态
            refreshPermissionStatus()
        }
    }

    /**
     * 刷新权限状态
     */
    fun refreshPermissionStatus() {
        val hasAllPermissions = PermissionChecker.hasAllPermissions(context)
        val missingPermissions = PermissionChecker.getMissingPermissions(context)
        AppLogger.event(TAG,
            "refreshPerm" to "RUN",
            "hasAll" to hasAllPermissions,
            "missing[${missingPermissions.size}]" to missingPermissions.joinToString(",").ifBlank { "<空，所有权限OK>" },
            "perm_notification" to (!missingPermissions.contains("通知")),
            "perm_fullscreen" to (!missingPermissions.contains("全屏通知")),
            "perm_batteryOpt" to (!missingPermissions.contains("电池优化")),
            "perm_overlay" to (!missingPermissions.contains("悬浮窗")),
            "autostartConfirmedByUser" to (!missingPermissions.contains("自启动确认")))
        _uiState.value = _uiState.value.copy(
            hasAllPermissions = hasAllPermissions,
            missingPermissions = missingPermissions
        )
    }

    /**
     * 从 DataStore 重新同步闹钟状态（从答题页/锁屏返回时调用，确保 UI 状态和落库一致）
     * - 落库为 NOT_SET：UI 立刻回到「未设置」，倒计时停
     * - 落库为 ACTIVE：重新按 DataStore 存储的 hour/minute 计算剩余时间
     * - 落库为 DISABLED：显示「本次闹钟已失效」，3 秒后回流 NOT_SET
     * - 落库为 RINGING：异常残留，清理回 NOT_SET
     */
    fun refreshAlarmState() {
        viewModelScope.launch {
            val state = dataStore.alarmState.first()
            val hour = dataStore.alarmHour.first()
            val minute = dataStore.alarmMinute.first()
            val lastHour = dataStore.lastHour.first()
            val lastMinute = dataStore.lastMinute.first()
            val ringtoneOption = dataStore.ringtoneOption.first()
            val triggerTime = dataStore.alarmTriggerTime.first()
            val isRingingNow = dataStore.isRinging.first()
            val permOk = _uiState.value.hasAllPermissions
            val remainingCalc = runCatching { alarmScheduler.calculateTimeUntilAlarm(hour, minute) }.getOrDefault(0L)
            val trigHuman = if (triggerTime > 0L) {
                val sdf = java.text.SimpleDateFormat("HH:mm:ss(MM-dd)", java.util.Locale.getDefault())
                val now = System.currentTimeMillis()
                val ds = (triggerTime - now) / 1000L
                "${sdf.format(java.util.Date(triggerTime))} diff=${if (ds >= 0) "${ds / 3600}h${(ds % 3600) / 60}m${ds % 60}s后响" else "${(-ds) / 3600}h${((-ds) % 3600) / 60}m${(-ds) % 60}s前已过"}"
            } else "null(无)"
            AppLogger.eventW(TAG,
                "refreshAlarmState" to "READ_DS_DONE",
                "state" to state.name,
                "alarmH" to hour,
                "alarmM" to minute,
                "triggerT" to trigHuman,
                "remainingCalc_s" to (remainingCalc / 1000L),
                "lastH" to lastHour,
                "lastM" to lastMinute,
                "isRinging" to isRingingNow,
                "ringtone" to "${ringtoneOption.displayName}(${ringtoneOption.volumeScale})",
                "hasAllPerm" to permOk,
                "missingCnt" to _uiState.value.missingPermissions.size)

            when (state) {
                AlarmState.NOT_SET -> {
                    AppLogger.d(TAG, "refreshAlarmState: 分支NOT_SET，取消倒计时，remaining=0")
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        alarmState = AlarmState.NOT_SET,
                        alarmHour = hour,
                        alarmMinute = minute,
                        isEditing = false,
                        isRinging = false,
                        remainingTimeMillis = 0L,
                        selectedHour = if (_uiState.value.alarmState == AlarmState.NOT_SET) lastHour else _uiState.value.selectedHour,
                        selectedMinute = if (_uiState.value.alarmState == AlarmState.NOT_SET) lastMinute else _uiState.value.selectedMinute,
                        ringtoneOption = ringtoneOption
                    )
                }
                AlarmState.ACTIVE -> {
                    val remaining = alarmScheduler.calculateTimeUntilAlarm(hour, minute)
                    AppLogger.d(TAG, "refreshAlarmState: 分支ACTIVE，计算剩余=${remaining/1000}秒，启动倒计时")
                    _uiState.value = _uiState.value.copy(
                        alarmState = AlarmState.ACTIVE,
                        alarmHour = hour,
                        alarmMinute = minute,
                        selectedHour = hour,
                        selectedMinute = minute,
                        isEditing = false,
                        isRinging = false,
                        remainingTimeMillis = remaining,
                        ringtoneOption = ringtoneOption
                    )
                    startCountdown()
                }
                AlarmState.DISABLED -> {
                    AppLogger.d(TAG, "refreshAlarmState: 分支DISABLED，取消倒计时，3秒后回流NOT_SET")
                    countdownJob?.cancel()
                    _uiState.value = _uiState.value.copy(
                        alarmState = AlarmState.DISABLED,
                        remainingTimeMillis = 0L,
                        isRinging = false,
                        ringtoneOption = ringtoneOption
                    )
                    delay(3000)
                    if (_uiState.value.alarmState == AlarmState.DISABLED) {
                        AppLogger.d(TAG, "refreshAlarmState: DISABLED停留3秒，回流NOT_SET+clearAlarm")
                        _uiState.value = _uiState.value.copy(
                            alarmState = AlarmState.NOT_SET
                        )
                        dataStore.clearAlarm()
                    }
                }
                AlarmState.RINGING -> {
                    // ================================================================
                    // v51 BugB 修复：RINGING 分支不能再"无脑 clearAlarm 清残留"！
                    // 上一版 Bug：20:36:02.822 Overlay addView 挂载成功 → 20:36:04.109 用户点了通知/系统冷启动 App →
                    //   HomeViewModel init → refreshAlarmState 读到 DS=RINGING → 当成"异常残留" → clearAlarm →
                    //   stopService(RingtoneService) → overlayController?.hide() → 铃声+答题界面瞬间全没
                    //   → 用户体验：只响了一下 / 答题界面出来又瞬间没 / 看不到做题
                    //
                    // 修复判定 = "两条证据任一成立 = 闹钟真的正在响中，不该清"；两条都不成立 = 真·异常残留才清
                    //   证据①：SP createdTrigger == DS triggerTime && triggerTime>0 （RingingActivity 真的 onCreate 过了）
                    //   证据②：canDrawOverlays=true （Overlay 正在挂/2.5s 兜底还在执行中/Overlay 其实已经挂载在系统窗口）
                    //
                    // 若真的在响：UI State 直接保留为 RINGING，不 clearAlarm、不动 Service 和 Overlay（前台响铃自然继续）
                    // ================================================================
                    val createdTrigger = RingingActivity.getCreatedTrigger(context)
                    val canDrawOverlayNow = PermissionChecker.canDrawOverlays(context)
                    val evidenceActOnCreate = (createdTrigger == triggerTime && triggerTime > 0L)
                    val sinceTriggerMs = if (triggerTime > 0L) System.currentTimeMillis() - triggerTime else Long.MAX_VALUE
                    val triggerInRingingWindow = sinceTriggerMs in -60_000L..MAX_VALID_RINGING_AGE_MS
                    // canDrawOverlays 只是权限，不是“悬浮窗当前还挂着”的证据。
                    // 真正可靠的持久证据是：DS明确标记正在响 + trigger仍在10分钟响铃窗口内。
                    val reallyRinging = isRingingNow && triggerInRingingWindow
                    RingingActivity.dumpRuntimePrefs(TAG, "HomeVM-RINGING-check", context)
                    AppLogger.eventW(TAG,
                        "HomeVM-RINGING-decision" to "START",
                        "createdTrigger" to createdTrigger,
                        "dsTrigger" to triggerTime,
                        "matchActOnCreate" to evidenceActOnCreate,
                        "canDrawOverlays" to canDrawOverlayNow,
                        "triggerInRingingWindow" to triggerInRingingWindow,
                        "reallyRinging(不应清)" to reallyRinging,
                        "sinceTrigger_ms" to sinceTriggerMs,
                        "dsIsRinging" to isRingingNow)
                    if (reallyRinging) {
                        // ✅ 真的正在响：UI State 保留为 RINGING，倒计时停，什么都不动（前台服务/铃声/Overlay 继续跑）
                        AppLogger.eventW(TAG,
                            "HomeVM-RINGING-decision" to "KEEP-RINGING(保留响铃状态，不动服务和界面)",
                            "reason" to if (evidenceActOnCreate) "createdTrigger==dsTrigger(RingingActivity真onCreate)" else "DS正在响且trigger仍在10分钟窗口内，恢复Service/Overlay")
                        countdownJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            alarmState = AlarmState.RINGING,
                            isRinging = true,
                            remainingTimeMillis = 0L,
                            ringtoneOption = ringtoneOption
                        )
                        // Activity 没有成功 onCreate 时，主动重发带 ACTION 的 Service Intent。
                        // 即便系统刚用 START_STICKY 重建过 Service，内部幂等保护也不会重复释放 MediaPlayer。
                        if (!evidenceActOnCreate) {
                            ensureRingingServiceRunning(triggerTime, "HomeVM-refresh-RINGING")
                        }
                    } else {
                        // 超过10分钟窗口或 DS 已明确 isRinging=false，才是真异常残留。
                        AppLogger.w(TAG, "refreshAlarmState: 分支RINGING（确认真·异常残留），清理回NOT_SET+clearAlarm")
                        countdownJob?.cancel()
                        _uiState.value = _uiState.value.copy(
                            alarmState = AlarmState.NOT_SET,
                            isRinging = false,
                            remainingTimeMillis = 0L
                        )
                        dataStore.clearAlarm()
                    }
                }
            }
        }
    }

    private fun ensureRingingServiceRunning(triggerTime: Long, source: String) {
        val serviceIntent = Intent(context, RingtoneService::class.java).apply {
            action = RingtoneService.ACTION_START_RINGING
            putExtra(AlarmScheduler.EXTRA_ALARM_TRIGGER_TIME, triggerTime)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            AppLogger.eventW(TAG,
                "ensureRingingService" to "REQUESTED",
                "source" to source,
                "triggerTime" to triggerTime)
        }.onFailure {
            AppLogger.e(TAG, "ensureRingingServiceRunning($source) 启动失败", it)
        }
    }

    /**
     * 启动倒计时刷新
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        AppLogger.d(TAG, "startCountdown: 启动倒计时循环，每60秒刷新")
        countdownJob = viewModelScope.launch {
            while (_uiState.value.alarmState == AlarmState.ACTIVE) {
                val remaining = alarmScheduler.getTimeUntilAlarm()
                _uiState.value = _uiState.value.copy(remainingTimeMillis = remaining)
                // 剩余时间归零后立刻读 DataStore：答题已关→NOT_SET；若系统尚未写入→下一圈再刷
                if (remaining <= 0L) {
                    val storedState = dataStore.alarmState.first()
                    AppLogger.w(TAG, "startCountdown: 剩余时间归零，读DataStore state=$storedState（非ACTIVE则刷新状态机）")
                    if (storedState != AlarmState.ACTIVE) {
                        refreshAlarmState()
                        break
                    }
                }
                delay(60000)
            }
            AppLogger.d(TAG, "startCountdown: 循环退出（state非ACTIVE）")
        }
    }

    /**
     * 显示 Dev 菜单（调试用）
     */
    fun showDevMenu() {
        _uiState.value = _uiState.value.copy(showDevMenu = true)
    }

    /**
     * 隐藏 Dev 菜单
     */
    fun hideDevMenu() {
        _uiState.value = _uiState.value.copy(showDevMenu = false)
    }

    /**
     * Dev 功能：立即触发闹钟（10 秒后）
     */
    fun triggerAlarmIn10Seconds() {
        viewModelScope.launch {
            AppLogger.d(TAG, "triggerAlarmIn10Seconds: Dev功能，设置10秒后闹钟")
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.SECOND, 10)
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val triggerTime = calendar.timeInMillis
            AppLogger.d(TAG, "triggerAlarmIn10Seconds: triggerTime=$triggerTime, 距离=${triggerTime - System.currentTimeMillis()}ms")

            // 先取消现有闹钟
            alarmScheduler.cancelAlarm()

            // 设置 10 秒后的闹钟
            val intent = android.content.Intent(context, com.mira.mathalarm.alarm.AlarmReceiver::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_ALARM_TRIGGER_TIME, triggerTime)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                AlarmScheduler.REQUEST_CODE_ALARM,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)

            try {
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                AppLogger.d(TAG, "triggerAlarmIn10Seconds: setAlarmClock成功，写DataStore")
                dataStore.saveAlarm(hour, minute, triggerTime)
                _uiState.value = _uiState.value.copy(
                    alarmState = AlarmState.ACTIVE,
                    alarmHour = hour,
                    alarmMinute = minute,
                    remainingTimeMillis = (triggerTime - System.currentTimeMillis()).coerceAtLeast(0L),
                    showDevMenu = false
                )
                startCountdown()
            } catch (e: SecurityException) {
                AppLogger.e(TAG, "triggerAlarmIn10Seconds: SecurityException，缺精确闹钟权限", e)
                // 精确闹钟权限缺失：回滚为未设置，并弹权限引导
                viewModelScope.launch {
                    dataStore.clearAlarm()
                }
                _uiState.value = _uiState.value.copy(
                    alarmState = AlarmState.NOT_SET,
                    remainingTimeMillis = 0L,
                    showPermissionGuideDialog = true
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "triggerAlarmIn10Seconds: 其他异常", e)
                viewModelScope.launch {
                    dataStore.clearAlarm()
                }
                _uiState.value = _uiState.value.copy(
                    alarmState = AlarmState.NOT_SET,
                    remainingTimeMillis = 0L,
                    showPermissionGuideDialog = true
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

/**
 * 首页 UI 状态
 */
data class HomeUiState(
    val alarmState: AlarmState = AlarmState.NOT_SET,
    val alarmHour: Int = 9,
    val alarmMinute: Int = 32,
    val selectedHour: Int = 9,
    val selectedMinute: Int = 32,
    val ringtoneOption: RingtoneOption = RingtoneOption.QINGCHEN,
    val remainingTimeMillis: Long = 0L,
    val showSetSuccessToast: Boolean = false,
    val showDisableDialog: Boolean = false,
    val showViewProblemDialog: Boolean = false,
    val showRingtonePicker: Boolean = false,
    val showOnboardingDialog: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    val showPermissionGuideDialog: Boolean = false,
    val hasAllPermissions: Boolean = true,
    val missingPermissions: List<String> = emptyList(),
    val isRinging: Boolean = false,
    val showDevMenu: Boolean = false,
    val isEditing: Boolean = false
)
