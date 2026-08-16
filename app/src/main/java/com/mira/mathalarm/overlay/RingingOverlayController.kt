package com.mira.mathalarm.overlay

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mira.mathalarm.data.AlarmDataStore
import com.mira.mathalarm.service.RingtoneService
import com.mira.mathalarm.ui.ringing.RingingScreen
import com.mira.mathalarm.ui.ringing.RingingViewModel
import com.mira.mathalarm.ui.theme.MathAlarmTheme
import com.mira.mathalarm.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A1 终极兜底：WindowManager TYPE_APPLICATION_OVERLAY 全屏悬浮答题页控制器
 *
 * 解决国产ROM（小米 HyperOS 等）后台启动 RingingActivity 被静默拦截的场景：
 *   - AlarmReceiver.startActivity()：无异常 return，但 RingingActivity.onCreate 从未触发
 *   - RingtoneService 1.5s 兜底 bringUpRingingActivity × 3 种 FLAG：仍未拉起
 *   - 2.5s 时检测 PermissionChecker.canDrawOverlays=true → show() → 100% 显示在锁屏/任何App之上
 *
 * 答对题 / 倒计时 10 分钟到点后的清场（和 RingingActivity.closeAlarmAndFinish 等价）：
 *   1) hide() removeViewImmediate 关悬浮
 *   2) RingtoneService.forceStopRingingAndNotify() 停铃声+清前台通知+清SP标记
 *   3) DataStore.clearAlarm() 状态机回 NOT_SET
 *
 * ComposeView 挂到 WindowManager 而非 Activity 时，必须手动提供三个 ViewTree owner：
 * LifecycleOwner / SavedStateRegistryOwner / ViewModelStoreOwner。缺少后两者会在首次
 * composition 时抛出未捕获异常，表现为 addView 成功后进程立即重启。
 */
class RingingOverlayController(private val context: Context) :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    companion object {
        private const val TAG = "RingingOverlayController"
    }

    // ===================== Lifecycle（Compose 里 lifecycle-aware 组件用） =====================
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    private var ownersCreated = false

    // ===================== WindowManager + View =====================
    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var isViewAttached = false
    private val dataStore: AlarmDataStore = AlarmDataStore(context)

    private var viewModel by mutableStateOf<RingingViewModel?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fullWakeLock: PowerManager.WakeLock? = null
    private var keyguardLock: KeyguardManager.KeyguardLock? = null
    private var isScreenTurnedOn = false

    private fun createViewTreeOwnersIfNeeded() {
        if (ownersCreated) return
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        ownersCreated = true
        AppLogger.d(TAG, "Compose ViewTree owners 已初始化：Lifecycle+SavedState+ViewModelStore")
    }

    private fun forceTurnScreenOnAndUnlock(context: Context) {
        // 1. PowerManager FULL_WAKE_LOCK: 强制屏幕亮起（即使锁屏/黑屏也亮）
        //    ACQUIRE_CAUSES_WAKEUP：拿到锁时立刻亮（不要求用户先操作）
        //    ON_AFTER_RELEASE：用户答对关悬浮后屏幕还亮几秒（避免立刻黑屏用户吓到）
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            fullWakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "MathAlarm:OverlayFullWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L)  // 和响铃10分钟对齐
            }
            isScreenTurnedOn = true
            AppLogger.d(TAG, "forceTurnScreenOnAndUnlock: FULL_WAKE_LOCK 已获取，屏幕亮起")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "forceTurnScreenOnAndUnlock: FULL_WAKE_LOCK 异常（可能机型限制）", t)
        }
        // 2. KeyguardLock：解锁系统锁屏（答完题后 re-enable）
        try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            @Suppress("DEPRECATION")
            keyguardLock = km.newKeyguardLock("MathAlarm:OverlayKeyguardLock").apply {
                disableKeyguard()
            }
            AppLogger.d(TAG, "forceTurnScreenOnAndUnlock: KeyguardLock 已解锁系统锁屏")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "forceTurnScreenOnAndUnlock: KeyguardLock disable异常（忽略）", t)
        }
        // 3. STREAM_ALARM 流强制拉满音量（用户侧铃声音量可能最小化）
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, alarmMax, 0)  // 0 = 不弹系统音量条
            AppLogger.d(TAG, "forceTurnScreenOnAndUnlock: STREAM_ALARM 音量已拉到max=$alarmMax")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "forceTurnScreenOnAndUnlock: 拉STREAM_ALARM音量异常（忽略）", t)
        }
    }

    private fun releaseScreenLocks() {
        try {
            fullWakeLock?.let {
                if (it.isHeld) it.release()
                AppLogger.d(TAG, "releaseScreenLocks: FULL_WAKE_LOCK 已释放")
            }
        } catch (t: Throwable) {
            AppLogger.w(TAG, "releaseScreenLocks: FULL_WAKE_LOCK release 异常（忽略）", t)
        }
        fullWakeLock = null
        try {
            @Suppress("DEPRECATION")
            keyguardLock?.reenableKeyguard()
            AppLogger.d(TAG, "releaseScreenLocks: KeyguardLock re-enable 锁屏")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "releaseScreenLocks: reenableKeyguard 异常（忽略）", t)
        }
        keyguardLock = null
        isScreenTurnedOn = false
    }

    fun show(triggerTime: Long) {
        if (isViewAttached) {
            AppLogger.w(TAG, "show() 重复调用，当前悬浮窗已挂在WindowManager上（忽略），triggerTime=$triggerTime")
            return
        }

        try {
            AppLogger.dumpWakeLockScreen(TAG, "Overlay-show-前", context)
            AppLogger.dumpDataStoreAll(TAG, "Overlay-show-前", dataStore)
            // 第一步先强制亮屏+解锁+拉满闹钟音量（不管锁屏与否都先执行，保证用户能看到听到）
            forceTurnScreenOnAndUnlock(context)
            AppLogger.dumpWakeLockScreen(TAG, "Overlay-show-亮屏解锁后", context, extraWl = fullWakeLock)

            createViewTreeOwnersIfNeeded()
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            this.windowManager = wm

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

            // MATCH_PARENT 在 Android 11+ 依然会默认按 systemBars insets 裁切；真机日志中
            // WindowMetrics=1200x2670，Overlay 却只有1200x2394，上下各露出一段壁纸。
            // 直接使用完整 WindowMetrics 像素尺寸，再在 LayoutParams 中关闭系统栏 inset 适配。
            val fullBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.currentWindowMetrics.bounds
            } else null

            val params = WindowManager.LayoutParams(
                fullBounds?.width() ?: ViewGroup.LayoutParams.MATCH_PARENT,
                fullBounds?.height() ?: ViewGroup.LayoutParams.MATCH_PARENT,
                type,
                buildFlags(),
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0; y = 0
                // 屏幕亮度拉满（即使 overlay 不触发 FULL_WAKE_LOCK 也能看到做题界面）
                screenBrightness = 1.0f
                buttonBrightness = 1.0f
                // 窗口动画：直接显示，不要系统淡入淡出过渡
                windowAnimations = 0
                // 答题页必须可获得输入焦点，并在数字键盘弹出时缩放布局。
                // FLAG_ALT_FOCUSABLE_IM 会把 Overlay 放到 IME 上方且禁止与键盘交互，不能用。
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // TYPE_APPLICATION_OVERLAY 也会继承默认 fitInsetsTypes(systemBars)。清空后
                    // 背景可画到透明状态栏/导航栏后方，系统图标仍在最上层。
                    setFitInsetsTypes(0)
                    setFitInsetsSides(0)
                    setFitInsetsIgnoringVisibility(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            AppLogger.event(TAG,
                "Overlay-targetFullScreen" to (fullBounds != null),
                "targetW" to params.width,
                "targetH" to params.height,
                "fitInsetsTypes" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) params.fitInsetsTypes else -1)

            val cv = ComposeView(context)
            this.composeView = cv
            cv.setViewTreeLifecycleOwner(this)
            cv.setViewTreeSavedStateRegistryOwner(this)
            cv.setViewTreeViewModelStoreOwner(this)
            cv.isFocusable = true
            cv.isFocusableInTouchMode = true
            lifecycleRegistry.currentState = Lifecycle.State.STARTED

            fun closeAlarm(reason: String) {
                CoroutineScope(Dispatchers.Main).launch { clearAlarmAndHide(reason) }
            }

            val vm = RingingViewModel()
            viewModel = vm
            vm.startCountdown(
                onFinish = {
                    AppLogger.d(TAG, "悬浮窗：10分钟倒计时到点，自动关闭")
                    closeAlarm("悬浮窗10分钟超时自动关闭")
                }
            )

            cv.setContent {
                MathAlarmTheme {
                    val p = vm.currentProblem
                    RingingScreen(
                        problem = p,
                        answer = vm.answer,
                        showError = vm.showError,
                        onAnswerChanged = { vm.answer = it },
                        onSubmit = {
                            if (vm.checkAnswer()) {
                                AppLogger.d(TAG, "悬浮窗：用户答对，正确答案=${p.answer}，清闹钟+关悬浮+停铃")
                                closeAlarm(reason = "悬浮窗答对题关闭")
                            } else {
                                AppLogger.w(TAG, "悬浮窗：用户答错，输入=${vm.answer},正确=${p.answer}")
                            }
                        },
                        remainingSeconds = vm.remainingSeconds
                    )
                }
            }

            cv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                    AppLogger.event(TAG,
                        "Overlay-OnViewAttached" to "Y",
                        "width" to v.width,
                        "height" to v.height,
                        "measuredW" to v.measuredWidth,
                        "measuredH" to v.measuredHeight,
                        "hasFocus" to v.hasFocus(),
                        "hasWindowFocus" to v.hasWindowFocus(),
                        "winToken" to (v.windowToken != null),
                        "displayId" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) v.display?.displayId else -1))
                }
                override fun onViewDetachedFromWindow(v: View) {
                    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                    AppLogger.d(TAG, "Overlay-OnViewDetached 触发（可能系统回收或用户清理）, w=${v.width}, h=${v.height}")
                }
            })

            AppLogger.dumpWmParams(TAG, "Overlay-before-addView", params, cv)
            wm.addView(cv, params)
            isViewAttached = true
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            cv.post {
                cv.requestFocus()
                AppLogger.event(TAG,
                    "Overlay-requestFocus" to cv.hasFocus(),
                    "attached" to cv.isAttachedToWindow,
                    "width" to cv.width,
                    "height" to cv.height)
            }
            AppLogger.d(TAG, "show(): TYPE_APPLICATION_OVERLAY 全屏悬浮答题页已挂载, ${params.width}x${params.height},type=$type,triggerTime=$triggerTime")
            AppLogger.dumpWmParams(TAG, "Overlay-after-addView", params, cv)

            // v50 调试：addView 成功后 300ms / 800ms 两次 dump UI 状态（UI 是异步挂载，第一次 300ms 可能还没 measure）
            val cvSnap = cv
            val paramsSnap = params
            val wmSnap = wm
            mainHandler.postDelayed({
                runCatching {
                    AppLogger.dumpWmParams(TAG, "Overlay-300ms", paramsSnap, cvSnap)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val b = wmSnap.currentWindowMetrics.bounds
                        AppLogger.event(TAG,
                            "Overlay-300ms-WM-metrics" to "Y",
                            "metricsW" to b.width(),
                            "metricsH" to b.height(),
                            "cvAttached" to cvSnap.isAttachedToWindow,
                            "cvShown" to cvSnap.isShown,
                            "cvWinFocus" to cvSnap.hasWindowFocus(),
                            "cvW" to cvSnap.width,
                            "cvH" to cvSnap.height,
                            "cvMeasW" to cvSnap.measuredWidth,
                            "cvMeasH" to cvSnap.measuredHeight)
                    }
                }.onFailure { AppLogger.w(TAG, "Overlay-300ms dump 异常", it) }
            }, 300L)
            mainHandler.postDelayed({
                runCatching {
                    AppLogger.dumpWmParams(TAG, "Overlay-800ms", paramsSnap, cvSnap)
                    AppLogger.dumpWakeLockScreen(TAG, "Overlay-800ms-screen", context)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val b = wmSnap.currentWindowMetrics.bounds
                        AppLogger.eventW(TAG,
                            "Overlay-800ms-FinalUI" to "SHOULD_BE_VISIBLE_NOW",
                            "metricsW" to b.width(),
                            "metricsH" to b.height(),
                            "cvAttached" to cvSnap.isAttachedToWindow,
                            "cvShown" to cvSnap.isShown,
                            "cvWinFocus" to cvSnap.hasWindowFocus(),
                            "cvW" to cvSnap.width,
                            "cvH" to cvSnap.height,
                            "cvMeasW" to cvSnap.measuredWidth,
                            "cvMeasH" to cvSnap.measuredHeight,
                            "lifecycle" to lifecycleRegistry.currentState.name,
                            "isViewAttached" to isViewAttached,
                            "composeViewNull" to (composeView == null))
                    }
                }.onFailure { AppLogger.w(TAG, "Overlay-800ms dump 异常", it) }
            }, 800L)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "show(): addView TYPE_APPLICATION_OVERLAY 失败", t)
            tryHideImmediately()
        }
    }

    private fun clearAlarmAndHide(reason: String) {
        try {
            hide()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "clearAlarmAndHide($reason): hide() 异常（继续清状态）", t)
        }
        try {
            RingtoneService.forceStopRingingAndNotify(context)
            AppLogger.d(TAG, "clearAlarmAndHide($reason): forceStopRingingAndNotify OK")
        } catch (t: Throwable) {
            AppLogger.e(TAG, "clearAlarmAndHide($reason): forceStopRingingAndNotify 异常", t)
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dataStore.clearAlarm()
                AppLogger.d(TAG, "clearAlarmAndHide($reason): dataStore.clearAlarm OK")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "clearAlarmAndHide($reason): dataStore.clearAlarm 异常", t)
            }
        }
    }

    fun hide() = tryHideImmediately()

    private fun tryHideImmediately() {
        if (!isViewAttached && !isScreenTurnedOn) return
        try {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        } catch (t: Throwable) {
            AppLogger.w(TAG, "tryHideImmediately: lifecycle DESTROYED 异常（忽略）", t)
        }
        try {
            windowManager?.removeViewImmediate(composeView)
            AppLogger.d(TAG, "hide(): WindowManager.removeViewImmediate 成功，悬浮窗已关闭")
        } catch (t: Throwable) {
            AppLogger.w(TAG, "hide(): removeViewImmediate 异常（忽略）", t)
        }
        try {
            releaseScreenLocks()
        } catch (_: Throwable) {}
        try {
            viewModel?.stopCountdown()
            viewModel = null
        } catch (t: Throwable) {
            AppLogger.w(TAG, "hide(): ViewModel 清理异常（忽略）", t)
        }
        composeView = null
        isViewAttached = false
        viewModelStore.clear()
    }

    private fun buildFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // HyperOS 使用透明手势/导航栏；不越过系统栏布局时，Overlay 高度
                // 会比完整屏幕少134px，底部就会透出锁屏壁纸。该 flag 只负责布局范围，
                // v54 已修复的 Compose ViewTree owners 才是原先进程重启的根因。
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        } else {
            // Android 11+ (API 30+) 废弃 FLAG_SHOW_WHEN_LOCKED/TURN_SCREEN_ON，但小米 HyperOS
            // 对这些 flag 仍然吃（TYPE_APPLICATION_OVERLAY 在锁屏上显示必须有它们）
            // 所以即便 API>=R 也再补一次（系统不认识就自动忽略，不会崩）
            @Suppress("DEPRECATION")
            flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        }
        return flags
    }
}
