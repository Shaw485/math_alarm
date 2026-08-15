package com.mira.mathalarm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.mira.mathalarm.ui.components.animatePressColor
import com.mira.mathalarm.ui.components.pressAnimatedScale
import com.mira.mathalarm.ui.theme.AppColors

/**
 * 首次启动引导弹窗
 * 对应 PRD M11：介绍 App 的核心功能
 */
@Composable
fun OnboardingDialog(
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
                    text = "关闭闹钟，先答对数学题",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 说明文字（PRD M11-C02/C03/C04）
                Text(
                    text = "这是一款\"防赖床\"闹钟\n响铃后需要答对一道数学题才能关闭\n帮你真正清醒起床",
                    fontSize = 15.sp,
                    color = AppColors.TextSecondary,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 按钮
                val btnInteraction = remember { MutableInteractionSource() }
                val btnBg by animatePressColor(
                    interactionSource = btnInteraction,
                    normalColor = AppColors.Primary,
                    pressedColor = AppColors.Primary.copy(alpha = 0.8f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressAnimatedScale(btnInteraction)
                        .background(btnBg, RoundedCornerShape(24.dp))
                        .clickable(
                            indication = null,
                            interactionSource = btnInteraction,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "知道了",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
