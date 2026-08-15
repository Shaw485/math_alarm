package com.mira.mathalarm.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mira.mathalarm.permission.PermissionChecker
import com.mira.mathalarm.ui.components.animatePressColor
import com.mira.mathalarm.ui.components.pressAnimatedAlpha
import com.mira.mathalarm.ui.components.pressAnimatedScale
import com.mira.mathalarm.ui.theme.AppColors

/**
 * 权限引导弹窗
 * 对应 PRD M12：统一的权限引导流程
 *
 * 关键规则：
 * ① 初始化 currentStep 时先找「第一个真实未授权」的步骤，已授权的自动跳过
 * ② 每次从系统设置/权限对话框回来，都必须**真实校验权限状态**，通过了才跳下一步，不通过就停留当前步骤
 * ③ 精确闹钟/全屏通知这两个只能跳系统设置的权限，用 StartActivityForResult launcher 等待用户返回再校验
 */
@Composable
fun PermissionGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // 需要申请的权限列表（按顺序显示，名字对应 PermissionChecker 中的校验方法）
    val permissionSteps = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add("通知权限")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add("全屏通知权限")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) add("电池优化权限")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) add("悬浮窗和后台弹出")  // ✅ v48新增：TYPE_APPLICATION_OVERLAY权限，国产ROM杀App后台startActivity被拦时，WindowManager全屏悬浮答题页唯一前提
            add("后台保活和自启动")   // ✅ 固定最后一步：国产 ROM 杀进程后必须开，否则闹钟收不到广播
            if (isEmpty()) add("通知权限")
        }
    }

    /** 检查第 index 步的权限是否真实已授权 */
    fun isStepAuthorized(index: Int): Boolean {
        return when (permissionSteps.getOrNull(index)) {
            "通知权限" -> PermissionChecker.areNotificationsEnabled(context)
            "全屏通知权限" -> PermissionChecker.canUseFullScreenIntent(context)
            "电池优化权限" -> PermissionChecker.isIgnoringBatteryOptimizations(context)
            "悬浮窗和后台弹出" -> PermissionChecker.canDrawOverlays(context)
            "后台保活和自启动" -> PermissionChecker.hasCompletedAutostartGuide(context)  // 用户只要点过去过设置页就算完成
            else -> true
        }
    }

    /** 找到第一个未授权的步骤 index，全部授权返回 null */
    fun findFirstUnauthorizedStep(): Int? {
        permissionSteps.indices.forEach { i ->
            if (!isStepAuthorized(i)) return i
        }
        return null
    }

    // 当前步骤：初始化时直接定位到「第一个真实未授权」的位置，跳过用户之前手动开过的
    var currentStep by remember { mutableIntStateOf(0) }

    // ✅ 电池优化 Provider 写入延迟修复：推进请求走 SharedFlow + LaunchedEffect 异步处理
    //    每次请求推进后先查一次 → 没过就 delay 450ms 再查第二次（兜电池优化的写入延迟）
    //    第二次查到电池优化=true 就自动 step++，用户体验和其他 3 步完全一致无卡顿
    val advanceRequests = remember {
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 4,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }
    suspend fun doAdvance() {
        // 第一次立即检查（其他 3 个权限：通知/全屏/自启动都直接过）
        var next = findFirstUnauthorizedStep()
        if (next == null) {
            onDismiss(); return
        }
        currentStep = next

        // 如果当前卡的那一步是「电池优化权限」，说明很可能是 Setting Provider 写入延迟（用户明明点允许了，立刻查仍=false）
        // → 额外再 delay 450ms 二次检查，过了就自动推进到下一个未授权步
        val currentPerm = permissionSteps.getOrNull(next)
        if (currentPerm == "电池优化权限" && !isStepAuthorized(next)) {
            delay(450)
            next = findFirstUnauthorizedStep()
            if (next == null) {
                onDismiss(); return
            }
            currentStep = next
        }
    }
    LaunchedEffect(advanceRequests) {
        advanceRequests.collect {
            doAdvance()
        }
    }
    /** 请求切到下一个未授权的步骤（异步推进，自动兜电池优化写入延迟） */
    fun requestAdvance() {
        advanceRequests.tryEmit(Unit)
    }
    /** 同步找第一步未授权（LaunchedEffect 初始化用） */
    fun advanceSyncOrNull() {
        val next = findFirstUnauthorizedStep()
        if (next == null) onDismiss() else currentStep = next
    }

    LaunchedEffect(Unit) {
        // 初始化：立即定位到「第一个真实未授权」，全部授权就关闭
        advanceSyncOrNull()
    }

    // 关键修复：每次从系统设置/授权页返回到 App（ON_RESUME）时，
    // 都重新真实校验所有权限状态（电池优化走 delay 二次检查），绝不依赖 launcher 回调 100% 触发
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, advanceRequests) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // ON_RESUME 走异步推进（自动兜 450ms 电池优化写入延迟）
                requestAdvance()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // =========================================================================
    // 三个 ActivityResult Launcher：回调里都要先校验真实权限，通过才走下一项
    // =========================================================================

    // 1) 通知权限：Android 13+ 运行时权限，用户点允许/拒绝都有回调
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // 不看 callback 里的 granted 值（有时回调是 false 但用户其实在设置里开了）
        // 统一用 PermissionChecker 真实检查一遍
        requestAdvance()
    }

    // 2) 电池优化：ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 弹系统确认框
    val batteryOptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        requestAdvance()
    }

    // 3) 通用系统设置页：精确闹钟、全屏通知都用这个跳 ACTION_* 设置页，等用户返回再检查
    val systemSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        requestAdvance()
    }

    // 4) 悬浮窗 & 后台弹出界面 (SYSTEM_ALERT_WINDOW / TYPE_APPLICATION_OVERLAY):
    //    Settings.ACTION_MANAGE_OVERLAY_PERMISSION 单项开关页，v48 终极兜底 Overlay 答题页必备前提
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 小米 HyperOS 这类国产ROM，用户点开关后Settings.canDrawOverlays也有Provider写入延迟，
        // 直接走 requestAdvance() → ON_RESUME + 异步推进的二次延迟检查会自动兜住
        requestAdvance()
    }

    // 5) 自启动 & 后台保活：国产 ROM 专属自启动页 launcher
    val autostartLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // ✅ 只要用户成功离开 App 去设置页再回来，就标记自启动引导已完成（持久化 KV）
        PermissionChecker.markAutostartGuideCompleted(context)
        requestAdvance()
    }

    /** 跳系统 App 详情页（兜底按钮） — 打开详情页后也用 launcher 等返回 */
    val appDetailsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        requestAdvance()
    }

    val goToAppDetails: () -> Unit = {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        appDetailsLauncher.launch(intent)
    }

    // 当前步骤对应的权限名
    val currentPermission = permissionSteps.getOrNull(currentStep) ?: "通知权限"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DimBackground)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .background(
                    AppColors.Surface,
                    RoundedCornerShape(16.dp)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "需要几项权限，才能准时叫醒你",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "为保证闹钟在锁屏、后台也能准时响铃并需答题关闭",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 权限列表：根据真实授权状态标 isDone
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    permissionSteps.forEachIndexed { index, name ->
                        PermissionRow(
                            name = "请授予$name",
                            isCurrent = index == currentStep,
                            isDone = isStepAuthorized(index)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 主按钮：去授权
                val authInteraction = remember { MutableInteractionSource() }
                val authBg by animatePressColor(
                    interactionSource = authInteraction,
                    normalColor = AppColors.Primary,
                    pressedColor = AppColors.Primary.copy(alpha = 0.8f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressAnimatedScale(authInteraction)
                        .background(authBg, RoundedCornerShape(24.dp))
                        .clickable(
                            indication = null,
                            interactionSource = authInteraction
                        ) {
                            when (currentPermission) {
                                "通知权限" -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(
                                            android.Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    } else {
                                        // Android 12 及以下默认通知已开，直接找下一个未授权
                                        requestAdvance()
                                    }
                                }
                                "全屏通知权限" -> {
                                    val intent = PermissionChecker.getFullScreenIntentSettingsIntent(context)
                                    if (intent != null) {
                                        systemSettingsLauncher.launch(intent)
                                    } else {
                                        requestAdvance()
                                    }
                                }
                                "电池优化权限" -> {
                                    val intent = PermissionChecker.getIgnoreBatteryOptimizationsIntent(context)
                                    if (intent != null) {
                                        batteryOptLauncher.launch(intent)
                                    } else {
                                        requestAdvance()
                                    }
                                }
                                "悬浮窗和后台弹出" -> {
                                    val intent = PermissionChecker.getOverlayPermissionIntent(context)
                                    if (intent != null) {
                                        overlayLauncher.launch(intent)
                                    } else {
                                        // Android 6.0 以下不需要悬浮窗权限，直接 step++
                                        requestAdvance()
                                    }
                                }
                                "后台保活和自启动" -> {
                                    // ✅ 小米/HyperOS跳MIUI自启动专属页，华为/OPPO/vivo跳各ROM专属页，其他ROM跳应用详情页
                                    val intent = PermissionChecker.getAutostartSettingsIntent(context)
                                    autostartLauncher.launch(intent)
                                }
                                else -> requestAdvance()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "去授权",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 兜底按钮：去系统设置开启
                val settingsInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .pressAnimatedAlpha(settingsInteraction, pressedAlpha = 0.7f)
                        .clickable(
                            indication = null,
                            interactionSource = settingsInteraction
                        ) {
                            goToAppDetails()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "去系统设置开启",
                        fontSize = 14.sp,
                        color = AppColors.TextTertiary
                    )
                }
            }
        }
    }
}

/**
 * 权限行
 */
@Composable
private fun PermissionRow(
    name: String,
    isCurrent: Boolean,
    isDone: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) AppColors.SurfaceLight else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        isDone -> AppColors.Success
                        isCurrent -> AppColors.Primary
                        else -> AppColors.TextTertiary
                    },
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            fontSize = 14.sp,
            color = if (isCurrent) AppColors.TextPrimary else AppColors.TextSecondary,
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal
        )
    }
}
