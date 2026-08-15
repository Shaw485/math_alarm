package com.mira.mathalarm.ui.home

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mira.mathalarm.data.RingtoneOption
import com.mira.mathalarm.ui.components.animatePressColor
import com.mira.mathalarm.ui.theme.AppColors

@Composable
fun RingtonePickerPopup(
    selectedOption: RingtoneOption,
    onOptionSelected: (RingtoneOption) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun playRingtone(option: RingtoneOption) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                val afd = context.resources.openRawResourceFd(option.resId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setVolume(option.volumeScale, option.volumeScale)
                start()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 打开弹窗时自动播放当前选中的铃声
    LaunchedEffect(Unit) {
        playRingtone(selectedOption)
    }

    // 关闭时释放
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
                .width(160.dp)
                .background(
                    AppColors.Surface,
                    RoundedCornerShape(12.dp)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                RingtoneOption.entries.forEachIndexed { index, option ->
                    val isSelected = option == selectedOption
                    val interactionSource = remember { MutableInteractionSource() }
                    val normalBg = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent
                    val pressedBg = Color.White.copy(alpha = 0.12f)
                    val bgColor by animatePressColor(
                        interactionSource = interactionSource,
                        normalColor = normalBg,
                        pressedColor = pressedBg
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(bgColor)
                            .clickable(
                                indication = null,
                                interactionSource = interactionSource
                            ) {
                                onOptionSelected(option)
                                playRingtone(option)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.displayName,
                            fontSize = 16.sp,
                            color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    if (index < RingtoneOption.entries.size - 1) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = AppColors.Divider,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
