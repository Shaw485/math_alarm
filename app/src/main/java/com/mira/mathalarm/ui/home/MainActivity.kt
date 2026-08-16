package com.mira.mathalarm.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mira.mathalarm.data.AlarmState
import com.mira.mathalarm.math.MathProblemGenerator
import com.mira.mathalarm.permission.PermissionChecker
import com.mira.mathalarm.ui.components.animatePressColor
import com.mira.mathalarm.ui.components.pressAnimatedAlpha
import com.mira.mathalarm.ui.components.pressAnimatedScale
import com.mira.mathalarm.ui.theme.AppColors
import com.mira.mathalarm.ui.theme.MathAlarmTheme
import com.mira.mathalarm.util.AppLogger

/**
 * 主页面 Activity
 * 唯一闹钟设置页，包含 M01-M12 全部模块
 * 严格按照 PRD 设计稿实现：纯黑背景、橙色主色、深灰磨砂滚轮
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MathAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppColors.Background
                ) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }

        // 初始化时刷新权限状态
        viewModel.refreshPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时先从 DataStore 全量同步闹钟状态（答完题关闭后回到初始状态）
        viewModel.refreshAlarmState()
        // 再刷新一次权限状态
        viewModel.refreshPermissionStatus()
    }
}

/**
 * 首页主屏幕
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showViewProblem by remember { mutableStateOf(false) }
    var sampleProblem by remember { mutableStateOf(MathProblemGenerator.generate()) }
    var showPermissionCheckDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 60.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // M01 顶部：标题
            TopBar()

            Spacer(modifier = Modifier.height(32.dp))

            // M01 状态文案
            StatusText(
                state = uiState.alarmState,
                remainingMinutes = uiState.remainingTimeMillis / 60000
            )

            Spacer(modifier = Modifier.height(40.dp))

            // M02 时间选择器（时 + 冒号 + 分）
            TimePicker(
                hour = uiState.selectedHour,
                minute = uiState.selectedMinute,
                onHourChanged = { viewModel.updateSelectedHour(it) },
                onMinuteChanged = { viewModel.updateSelectedMinute(it) }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // M03 查看题目 + M10 铃声选择（左右并排）
            SecondaryActions(
                ringtoneName = uiState.ringtoneOption.displayName,
                isRingtoneOpen = uiState.showRingtonePicker,
                onRingtoneClick = { viewModel.showRingtonePicker() },
                onViewProblemClick = {
                    sampleProblem = MathProblemGenerator.generate()
                    showViewProblem = true
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            // M04 主按钮 / M05 次按钮
            MainButtons(
                state = uiState.alarmState,
                isEditing = uiState.isEditing,
                onConfirmClick = {
                    when {
                        uiState.alarmState != AlarmState.ACTIVE -> viewModel.confirmSetAlarm()
                        uiState.isEditing -> viewModel.confirmSetAlarm()
                        // 用户已经先滑动了时间，再点「修改闹钟」：直接保存。
                        // 避免只进入编辑态、但系统 AlarmManager 仍保留旧时间。
                        uiState.selectedHour != uiState.alarmHour ||
                                uiState.selectedMinute != uiState.alarmMinute -> viewModel.confirmSetAlarm()
                        // 时间还没改：先进入编辑态，按钮随即变成「确认修改」。
                        else -> viewModel.enterEditMode()
                    }
                },
                onDisableClick = { viewModel.showDisableDialog() }
            )

            Spacer(modifier = Modifier.weight(1f))

            // M06 权限风险提示（未全部授权时显示）
            if (!uiState.hasAllPermissions) {
                PermissionWarning(
                    onClick = { viewModel.showPermissionGuideDialog() }
                )
            }
        }

        // 左下角：权限检查按钮（悬浮在 Column 之下，始终可见）
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 16.dp)
        ) {
            val checkInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressAnimatedAlpha(checkInteraction, pressedAlpha = 0.7f)
                    .clickable(
                        indication = null,
                        interactionSource = checkInteraction
                    ) { showPermissionCheckDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "权限检查",
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary
                )
            }
        }

        // 右下角：导出日志按钮（和左下角权限检查对称，用于真机调试一键分享log）
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 16.dp)
        ) {
            val exportInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressAnimatedAlpha(exportInteraction, pressedAlpha = 0.7f)
                    .clickable(
                        indication = null,
                        interactionSource = exportInteraction
                    ) {
                        val logFile = AppLogger.getLatestLogFile()
                        if (logFile == null) {
                            Toast.makeText(ctx, "暂无日志", Toast.LENGTH_SHORT).show()
                            return@clickable
                        }
                        val uri = FileProvider.getUriForFile(
                            ctx,
                            "${ctx.packageName}.fileprovider",
                            logFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        ctx.startActivity(
                            Intent.createChooser(shareIntent, "导出日志到")
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "导出日志",
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary
                )
            }
        }

        // 铃声选择浮窗
        if (uiState.showRingtonePicker) {
            RingtonePickerPopup(
                selectedOption = uiState.ringtoneOption,
                onOptionSelected = { option ->
                    viewModel.selectRingtone(option)
                },
                onDismiss = { viewModel.hideRingtonePicker() }
            )
        }

        // 查看题目弹窗
        if (showViewProblem || uiState.showViewProblemDialog) {
            ViewProblemDialog(
                problem = sampleProblem,
                onRefresh = { sampleProblem = MathProblemGenerator.generate() },
                onDismiss = {
                    showViewProblem = false
                    viewModel.hideViewProblemDialog()
                }
            )
        }

        // 失效确认弹窗
        if (uiState.showDisableDialog) {
            DisableAlarmDialog(
                onConfirm = { viewModel.disableAlarm() },
                onDismiss = { viewModel.hideDisableDialog() }
            )
        }

        // 首次启动引导
        if (uiState.showOnboardingDialog) {
            OnboardingDialog(
                onDismiss = { viewModel.markOnboardingSeen() }
            )
        }

        // 权限引导
        if (uiState.showPermissionGuideDialog) {
            PermissionGuideDialog(
                onDismiss = { viewModel.dismissPermissionGuide() }
            )
        }

        // 权限状态检查弹窗（用户点击左下角按钮时显示）
        if (showPermissionCheckDialog) {
            PermissionCheckDialog(
                onDismiss = { showPermissionCheckDialog = false },
                onGoGuide = {
                    showPermissionCheckDialog = false
                    viewModel.showPermissionGuideDialog()
                }
            )
        }

        // 设置成功 Toast（M08：将在 X 小时 X 分钟后响铃）
        if (uiState.showSetSuccessToast) {
            val remainingMinutes = uiState.remainingTimeMillis / 60000
            val hours = remainingMinutes / 60
            val mins = remainingMinutes % 60
            val timeText = if (hours > 0) {
                "${hours} 小时 ${mins} 分钟"
            } else {
                "${mins} 分钟"
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            AppColors.Surface.copy(alpha = 0.9f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "将在 $timeText 后响铃",
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * 顶部栏：标题
 */
@Composable
private fun TopBar() {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "闹钟",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * 状态文案
 * 遵循 PRD M01：
 * - 未设置：未设置
 * - 生效中：生效中・距离响铃还有 X 小时 X 分钟
 * - 本次闹钟已失效：本次闹钟已失效
 * - 响铃中：响铃中
 */
@Composable
private fun StatusText(state: AlarmState, remainingMinutes: Long = 0) {
    val (text, color) = when (state) {
        AlarmState.NOT_SET -> "未设置" to AppColors.TextSecondary
        AlarmState.ACTIVE -> {
            val hours = remainingMinutes / 60
            val mins = remainingMinutes % 60
            val timeText = if (hours > 0) {
                "${hours} 小时 ${mins} 分钟"
            } else {
                "${mins} 分钟"
            }
            "生效中・距离响铃还有 $timeText" to AppColors.Success
        }
        AlarmState.RINGING -> "响铃中" to AppColors.Error
        AlarmState.DISABLED -> "本次闹钟已失效" to AppColors.TextSecondary
    }

    Text(
        text = text,
        fontSize = 16.sp,
        color = color,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

/**
 * 时间选择器：时 + 冒号 + 分
 */
@Composable
private fun TimePicker(
    hour: Int,
    minute: Int,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit
) {
    val hours = (0..23).map { it.toString().padStart(2, '0') }
    val minutes = (0..59).map { it.toString().padStart(2, '0') }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 时
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "时",
                fontSize = 14.sp,
                color = AppColors.TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            WheelPicker(
                items = hours,
                selectedIndex = hour,
                onIndexChanged = onHourChanged,
                modifier = Modifier.width(120.dp)
            )
        }

        // 冒号
        Text(
            text = ":",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // 分
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "分",
                fontSize = 14.sp,
                color = AppColors.TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            WheelPicker(
                items = minutes,
                selectedIndex = minute,
                onIndexChanged = onMinuteChanged,
                modifier = Modifier.width(120.dp)
            )
        }
    }
}

/**
 * 次级操作：铃声 | 查看题目
 */
@Composable
private fun SecondaryActions(
    ringtoneName: String,
    isRingtoneOpen: Boolean,
    onRingtoneClick: () -> Unit,
    onViewProblemClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ringtoneInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .pressAnimatedAlpha(ringtoneInteraction, pressedAlpha = 0.7f)
                .background(
                    if (isRingtoneOpen) AppColors.Primary.copy(alpha = 0.2f) else Color.Transparent,
                    androidx.compose.foundation.shape.CircleShape
                )
                .clickable(
                    indication = null,
                    interactionSource = ringtoneInteraction,
                    onClick = onRingtoneClick
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "铃声",
                fontSize = 17.sp,
                color = AppColors.Primary
            )
        }

        // 中间竖线分隔
        Divider(
            modifier = Modifier
                .height(20.dp)
                .width(1.dp),
            color = AppColors.Divider
        )

        val problemInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .pressAnimatedAlpha(problemInteraction, pressedAlpha = 0.7f)
                .clickable(
                    indication = null,
                    interactionSource = problemInteraction,
                    onClick = onViewProblemClick
                )
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "查看题目",
                fontSize = 17.sp,
                color = AppColors.Primary
            )
        }
    }
}

/**
 * 主按钮区域
 * 未设置状态：只有确认按钮
 * 生效中状态：确认（修改闹钟） + 失效
 */
@Composable
private fun MainButtons(
    state: AlarmState,
    isEditing: Boolean,
    onConfirmClick: () -> Unit,
    onDisableClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 主按钮（确认 / 修改闹钟）
        val confirmInteraction = remember { MutableInteractionSource() }
        val confirmBg by animatePressColor(
            interactionSource = confirmInteraction,
            normalColor = AppColors.Primary,
            pressedColor = AppColors.Primary.copy(alpha = 0.8f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressAnimatedScale(confirmInteraction)
                .background(confirmBg, RoundedCornerShape(28.dp))
                .clickable(
                    indication = null,
                    interactionSource = confirmInteraction,
                    onClick = onConfirmClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    state == AlarmState.ACTIVE && isEditing -> "确认修改"
                    state == AlarmState.ACTIVE -> "修改闹钟"
                    else -> "确认设置"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        // 生效中状态显示失效按钮
        if (state == AlarmState.ACTIVE) {
            Spacer(modifier = Modifier.height(16.dp))
            val disableInteraction = remember { MutableInteractionSource() }
            val disableBg by animatePressColor(
                interactionSource = disableInteraction,
                normalColor = AppColors.Surface,
                pressedColor = AppColors.SurfaceLight
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressAnimatedScale(disableInteraction)
                    .background(disableBg, RoundedCornerShape(28.dp))
                    .clickable(
                        indication = null,
                        interactionSource = disableInteraction,
                        onClick = onDisableClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "失效",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Error
                )
            }
        }
    }
}

/**
 * 权限风险提示条
 */
@Composable
private fun PermissionWarning(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "提醒权限未开启，闹钟可能无法正常响铃",
            fontSize = 13.sp,
            color = AppColors.Warning,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "去开启",
            fontSize = 14.sp,
            color = AppColors.Primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() }
        )
    }
}

/**
 * 权限状态检查弹窗
 * 点击左下角「权限检查」按钮时显示
 * 实时校验五项系统权限，并单独展示无法程序化读取的自启动确认状态。
 */
@Composable
private fun PermissionCheckDialog(
    onDismiss: () -> Unit,
    onGoGuide: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 每次打开弹窗时实时检查权限状态
    val notifEnabled = remember { mutableStateOf(PermissionChecker.areNotificationsEnabled(context)) }
    val exactAlarmEnabled = remember { mutableStateOf(PermissionChecker.canScheduleExactAlarms(context)) }
    val fullscreenEnabled = remember { mutableStateOf(PermissionChecker.canUseFullScreenIntent(context)) }
    val batteryEnabled = remember { mutableStateOf(PermissionChecker.isIgnoringBatteryOptimizations(context)) }
    val overlayEnabled = remember { mutableStateOf(PermissionChecker.canDrawOverlays(context)) }
    val autostartConfirmed = remember {
        mutableStateOf(PermissionChecker.isAutostartConfirmedByUser(context))
    }

    // 每次 recomposition（如返回弹窗后）重新检查
    LaunchedEffect(Unit) {
        notifEnabled.value = PermissionChecker.areNotificationsEnabled(context)
        exactAlarmEnabled.value = PermissionChecker.canScheduleExactAlarms(context)
        fullscreenEnabled.value = PermissionChecker.canUseFullScreenIntent(context)
        batteryEnabled.value = PermissionChecker.isIgnoringBatteryOptimizations(context)
        overlayEnabled.value = PermissionChecker.canDrawOverlays(context)
        autostartConfirmed.value = PermissionChecker.isAutostartConfirmedByUser(context)
    }

    val allOk = notifEnabled.value && exactAlarmEnabled.value && fullscreenEnabled.value &&
        batteryEnabled.value && overlayEnabled.value && autostartConfirmed.value

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
                .background(AppColors.Surface, RoundedCornerShape(16.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "权限状态检查",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (allOk) {
                        "系统权限已开启，自启动已由你确认"
                    } else {
                        "仍有项目未开启或未确认，闹钟可能无法响铃"
                    },
                    fontSize = 13.sp,
                    color = if (allOk) AppColors.Success else AppColors.Warning,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                PermissionCheckRow("通知权限", notifEnabled.value)
                PermissionCheckRow("精确闹钟权限", exactAlarmEnabled.value)
                PermissionCheckRow("全屏通知权限", fullscreenEnabled.value)
                PermissionCheckRow("电池优化权限", batteryEnabled.value)
                PermissionCheckRow("悬浮窗权限", overlayEnabled.value)
                PermissionCheckRow(
                    name = "后台保活和自启动",
                    isGranted = autostartConfirmed.value,
                    grantedText = "已确认",
                    deniedText = "待确认"
                )

                Spacer(modifier = Modifier.height(20.dp))

                val btnInteraction = remember { MutableInteractionSource() }
                val btnBg by animatePressColor(
                    interactionSource = btnInteraction,
                    normalColor = AppColors.Primary,
                    pressedColor = AppColors.Primary.copy(alpha = 0.8f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pressAnimatedScale(btnInteraction)
                        .background(btnBg, RoundedCornerShape(22.dp))
                        .clickable(
                            indication = null,
                            interactionSource = btnInteraction
                        ) {
                            if (allOk) {
                                // 即使已经确认，用户也能随时重新进入自启动设置复查，
                                // 不再出现“选项消失、无处重开”的问题。
                                context.startActivity(
                                    PermissionChecker.getAutostartSettingsIntent(context)
                                )
                                onDismiss()
                            } else {
                                onGoGuide()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (allOk) "复查自启动设置" else "去开启或确认",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "关闭",
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun PermissionCheckRow(
    name: String,
    isGranted: Boolean,
    grantedText: String = "已开启",
    deniedText: String = "未开启"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isGranted) AppColors.Success else AppColors.Error,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = 14.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isGranted) grantedText else deniedText,
            fontSize = 13.sp,
            color = if (isGranted) AppColors.Success else AppColors.Error,
            fontWeight = FontWeight.Medium
        )
    }
}
