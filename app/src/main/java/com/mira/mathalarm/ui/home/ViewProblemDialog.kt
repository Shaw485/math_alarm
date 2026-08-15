package com.mira.mathalarm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mira.mathalarm.math.MathProblem
import com.mira.mathalarm.ui.components.animatePressColor
import com.mira.mathalarm.ui.components.pressAnimatedScale
import com.mira.mathalarm.ui.theme.AppColors

/**
 * 查看题目弹窗
 * 对应 PRD M03：展示示例数学题，可换一题
 */
@Composable
fun ViewProblemDialog(
    problem: MathProblem,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
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
                // 标题
                Text(
                    text = "响铃后需要回答的题目示例",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 题目公式（单独一排居中大字号）
                Text(
                    text = problem.formula,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // =? 单独一排居中，稍小字号
                Text(
                    text = "= ?",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 说明文字 1（PRD M03-C08）
                Text(
                    text = "只有回答正确，才能关闭闹钟",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 说明文字 2（PRD M03-C09）
                Text(
                    text = "你可以点击「换一题」查看其他示例",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 主按钮
                val confirmInteraction = remember { MutableInteractionSource() }
                val confirmBg by animatePressColor(
                    interactionSource = confirmInteraction,
                    normalColor = AppColors.Primary,
                    pressedColor = AppColors.Primary.copy(alpha = 0.8f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressAnimatedScale(confirmInteraction)
                        .background(confirmBg, RoundedCornerShape(24.dp))
                        .clickable(
                            indication = null,
                            interactionSource = confirmInteraction,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "我知道了",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 换一题按钮
                val refreshInteraction = remember { MutableInteractionSource() }
                val refreshBg by animatePressColor(
                    interactionSource = refreshInteraction,
                    normalColor = AppColors.SurfaceLight,
                    pressedColor = AppColors.SurfaceLight.copy(alpha = 0.7f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressAnimatedScale(refreshInteraction)
                        .background(refreshBg, RoundedCornerShape(24.dp))
                        .clickable(
                            indication = null,
                            interactionSource = refreshInteraction,
                            onClick = onRefresh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "换一题",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary
                    )
                }
            }
        }
    }
}
