package com.mira.mathalarm.ui.ringing

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mira.mathalarm.alarm.AlarmReceiver
import com.mira.mathalarm.data.AlarmDataStore
import com.mira.mathalarm.data.AlarmState
import com.mira.mathalarm.math.MathProblem
import com.mira.mathalarm.math.MathProblemGenerator
import com.mira.mathalarm.service.RingtoneService
import com.mira.mathalarm.ui.components.animatePressColor
import com.mira.mathalarm.ui.components.pressAnimatedScale
import com.mira.mathalarm.ui.theme.AppColors
import com.mira.mathalarm.ui.theme.MathAlarmTheme
import com.mira.mathalarm.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 响铃答题 Activity
 * 对应 PRD M09：闹钟响铃时全屏弹出，必须答对数学题才能关闭
 */
class RingingActivity : ComponentActivity() {

    companion object {
        private const val TAG = "RingingActivity"
        private const val RUNTIME_PREFS = "mathalarm_runtime_prefs"
        private const val KEY_RINGING_ACTIVITY_CREATED_TRIGGER = "ringing_activity_created_for_trigger"
        /** 给 AlarmReceiver 读的：检查答题页是否真的 onCreate 成功（防止国产ROM静默拦截startActivity后我们以为成功了） */
        fun getCreatedTrigger(context: Context): Long =
            context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_RINGING_ACTIVITY_CREATED_TRIGGER, 0L)
        /** 答题页 onCreate 第一时间调用，写入 SP 作为「真的被拉起」的铁证 */
        fun markCreatedTrigger(context: Context, triggerTime: Long) {
            context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_RINGING_ACTIVITY_CREATED_TRIGGER, triggerTime)
                .apply()
        }
        fun clearCreatedTrigger(context: Context) {
            context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_RINGING_ACTIVITY_CREATED_TRIGGER).apply()
        }
        /** v50 调试辅助：dump 整个 runtime SP，看除了 createdTrigger 还有什么脏数据、trigger 写入对不对 */
        fun dumpRuntimePrefs(tag: String, prefix: String, context: Context) {
            runCatching {
                val sp = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
                val all = sp.all
                if (all.isEmpty()) {
                    AppLogger.d(tag, "$prefix RuntimeSP[$RUNTIME_PREFS]: <空>")
                    return
                }
                val body = all.toSortedMap().entries.joinToString("  ") { (k, v) ->
                    "$k=${v?.let { "${it::class.java.simpleName}:$it" } ?: "null"}"
                }
                AppLogger.event(tag, "${prefix}_RuntimeSP[size=${all.size}]" to body)
            }.onFailure { AppLogger.w(tag, "$prefix dumpRuntimePrefs 异常", it) }
        }
    }

    private val viewModel: RingingViewModel by viewModels()

    private fun closeAlarmAndFinish(reason: String) {
        AppLogger.d(TAG, "closeAlarmAndFinish: 关闭原因=$reason")
        // 1. 清除 AlarmReceiver 发的全屏通知（不然会一直挂在状态栏）
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AlarmReceiver.FULLSCREEN_NOTIFICATION_ID)
            AppLogger.d(TAG, "全屏通知 id=${AlarmReceiver.FULLSCREEN_NOTIFICATION_ID} 已取消")
        } catch (e: Exception) {
            AppLogger.e(TAG, "取消全屏通知失败（忽略）", e)
        }
        // 2. 异步 DataStore 清理，完成后打印日志便于定位是否真的写回NOT_SET
        viewModel.viewModelScope.launch {
            AlarmDataStore(this@RingingActivity).clearAlarm()
            AppLogger.d(TAG, "AlarmDataStore.clearAlarm() 调用完成，DataStore状态应已变为NOT_SET")
        }
        // 3. 停掉响铃服务
        try {
            stopService(Intent(this, RingtoneService::class.java))
            AppLogger.d(TAG, "RingtoneService.stopService 调用完成")
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止RingtoneService失败（忽略）", e)
        }
        // 4. 关闭答题页
        finish()
        AppLogger.d(TAG, "finish() 调用完成，答题页关闭，返回MainActivity")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val triggerTime = intent.getLongExtra(com.mira.mathalarm.alarm.AlarmScheduler.EXTRA_ALARM_TRIGGER_TIME, 0L)
        // ✅ 先写 SharedPreferences KV（AlarmReceiver 的 500ms/1500ms 轮询会读这个，读到就说明答题页真的起来了）
        RingingActivity.markCreatedTrigger(this, triggerTime)
        AppLogger.d(TAG, "onCreate: 响铃答题页打开（已写SP创建标记）, intent.action=${intent.action}")
        AppLogger.d(TAG, "onCreate: triggerTime=$triggerTime, now=${System.currentTimeMillis()}, 延迟=${System.currentTimeMillis() - triggerTime}ms")

        // 设置锁屏显示和点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // 解锁键盘锁
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 设置闹钟音量最大
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        setContent {
            MathAlarmTheme {
                RingingScreen(
                    problem = viewModel.currentProblem,
                    answer = viewModel.answer,
                    showError = viewModel.showError,
                    onAnswerChanged = { viewModel.answer = it },
                    onSubmit = {
                        if (viewModel.checkAnswer()) {
                            // 答对了：停止响铃+清全屏通知+清DataStore+关闭页面
                            AppLogger.d(TAG, "用户答对题目，正确答案=${viewModel.currentProblem.answer}，关闭闹钟")
                            closeAlarmAndFinish("答对题关闭")
                        } else {
                            AppLogger.w(TAG, "用户答错，输入=${viewModel.answer}，正确答案=${viewModel.currentProblem.answer}")
                        }
                    },
                    remainingSeconds = viewModel.remainingSeconds
                )
            }
        }

        // 启动响铃服务（用带ACTION的intent，和AlarmReceiver一致，确保Service进入响铃分支）
        val intent = Intent(this, RingtoneService::class.java).apply {
            action = RingtoneService.ACTION_START_RINGING
            putExtra(RingtoneService.EXTRA_RINGING_ACTIVITY_VISIBLE, true)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            AppLogger.d(TAG, "onCreate: RingtoneService已请求启动（ACTION_START_RINGING）")
        } catch (e: Exception) {
            AppLogger.e(TAG, "onCreate: 启动RingtoneService失败", e)
        }

        // 开始倒计时（10 分钟兜底）
        viewModel.startCountdown {
            // 10 分钟后自动停止，回到未设置状态
            AppLogger.w(TAG, "10分钟兜底超时，自动关闭闹钟")
            closeAlarmAndFinish("10分钟超时自动关闭")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.d(TAG, "onDestroy: RingingActivity销毁")
        viewModel.stopCountdown()
    }

    override fun onBackPressed() {
        // 禁止返回键关闭
        // 必须答题才能关闭
    }
}

/**
 * 响铃页面 ViewModel
 */
class RingingViewModel : ViewModel() {
    var currentProblem by mutableStateOf(MathProblemGenerator.generate())
        private set

    var answer by mutableStateOf("")

    var showError by mutableStateOf(false)

    var remainingSeconds by mutableStateOf(600)
        private set

    private var countdownJob: kotlinx.coroutines.Job? = null

    /**
     * 校验答案
     * 答对返回 true，答错返回 false 并刷新新题
     */
    fun checkAnswer(): Boolean {
        val isCorrect = currentProblem.checkAnswer(answer)
        if (isCorrect) {
            return true
        } else {
            // 答错：刷新新题，显示错误提示
            currentProblem = MathProblemGenerator.generate()
            answer = ""
            showError = true
            viewModelScope.launch {
                delay(1500)
                showError = false
            }
            return false
        }
    }

    /**
     * 开始 2 分钟倒计时
     */
    fun startCountdown(onFinish: () -> Unit) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
            }
            onFinish()
        }
    }

    fun stopCountdown() {
        countdownJob?.cancel()
    }
}

/**
 * 响铃答题屏幕
 */
@Composable
fun RingingScreen(
    problem: MathProblem,
    answer: String,
    showError: Boolean,
    onAnswerChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    remainingSeconds: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 80.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：时间 + 倒计时
            Text(
                text = formatTime(remainingSeconds),
                fontSize = 16.sp,
                color = AppColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 标题（PRD M09-C01）
            Text(
                text = "请回答下面的数学题以关闭闹钟",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 题目固定分两行。不再依赖 Text 按屏幕宽度自动换行：自动换行时字体
            // 行高会让第二行「= ?」和第一行视觉粘连。
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = problem.formula,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "= ?",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 答案输入框（显示用）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        AppColors.Surface,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = answer.ifBlank { "请输入答案" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (answer.isBlank()) AppColors.TextTertiary else AppColors.TextPrimary
                )
            }

            // 错误提示
            if (showError) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "答案错误，请重试",
                    fontSize = 14.sp,
                    color = AppColors.Error
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 数字键盘
            NumberKeypad(
                onNumberClick = { num ->
                    if (answer.length < 6) {
                        onAnswerChanged(answer + num)
                    }
                },
                onDeleteClick = {
                    if (answer.isNotEmpty()) {
                        onAnswerChanged(answer.dropLast(1))
                    }
                },
                onSubmit = onSubmit
            )
        }
    }
}

/**
 * 数字键盘
 */
@Composable
private fun NumberKeypad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onSubmit: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val interactionSource = remember { MutableInteractionSource() }
                        val normalBg = if (key == "⌫") AppColors.SurfaceLight else AppColors.Surface
                        val pressedBg = Color.White.copy(alpha = 0.15f)
                        val bgColor by animatePressColor(
                            interactionSource = interactionSource,
                            normalColor = normalBg,
                            pressedColor = pressedBg
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .pressAnimatedScale(interactionSource)
                                .background(bgColor, RoundedCornerShape(12.dp))
                                .clickable(
                                    indication = null,
                                    interactionSource = interactionSource
                                ) {
                                    when (key) {
                                        "⌫" -> onDeleteClick()
                                        else -> onNumberClick(key)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.TextPrimary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 确认按钮
        val submitInteractionSource = remember { MutableInteractionSource() }
        val submitBg by animatePressColor(
            interactionSource = submitInteractionSource,
            normalColor = AppColors.Primary,
            pressedColor = AppColors.Primary.copy(alpha = 0.8f)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressAnimatedScale(submitInteractionSource)
                .background(submitBg, RoundedCornerShape(28.dp))
                .clickable(
                    indication = null,
                    interactionSource = submitInteractionSource,
                    onClick = onSubmit
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "确认",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * 格式化倒计时显示
 */
private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "还剩 ${min}:${sec.toString().padStart(2, '0')}"
}
