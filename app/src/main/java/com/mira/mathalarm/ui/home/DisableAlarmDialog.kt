package com.mira.mathalarm.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
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
import com.mira.mathalarm.ui.theme.AppColors

/**
 * 失效确认弹窗
 * 对应 PRD M07：确认将本次闹钟设为失效
 */
@Composable
fun DisableAlarmDialog(
    onConfirm: () -> Unit,
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
                .padding(horizontal = 48.dp)
                .background(
                    AppColors.Surface,
                    RoundedCornerShape(14.dp)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                // 标题（PRD M07-C01）
                Text(
                    text = "确认将本次闹钟设为失效？",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 分隔线
                Divider(
                    color = AppColors.Divider,
                    thickness = 0.5.dp
                )

                // 底部按钮：取消 + 确认失效（左右并排）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    // 取消
                    val cancelInteraction = remember { MutableInteractionSource() }
                    val cancelBg by animatePressColor(
                        interactionSource = cancelInteraction,
                        normalColor = Color.Transparent,
                        pressedColor = Color.White.copy(alpha = 0.08f)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(cancelBg)
                            .clickable(
                                indication = null,
                                interactionSource = cancelInteraction,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 16.sp,
                            color = AppColors.Primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 中间竖线
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .fillMaxSize()
                            .background(AppColors.Divider)
                    )

                    // 确认失效
                    val confirmInteraction = remember { MutableInteractionSource() }
                    val confirmBg by animatePressColor(
                        interactionSource = confirmInteraction,
                        normalColor = Color.Transparent,
                        pressedColor = Color.White.copy(alpha = 0.08f)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(confirmBg)
                            .clickable(
                                indication = null,
                                interactionSource = confirmInteraction,
                                onClick = onConfirm
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "确认失效",
                            fontSize = 16.sp,
                            color = AppColors.Error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
