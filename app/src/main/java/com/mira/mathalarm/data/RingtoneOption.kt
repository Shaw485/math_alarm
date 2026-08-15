package com.mira.mathalarm.data

import com.mira.mathalarm.R

/**
 * 铃声选项枚举
 * 对应 PRD M10 铃声选择模块：清晨 / 曙光 / 山岚
 */
enum class RingtoneOption(
    val displayName: String,
    val resId: Int,
    val volumeScale: Float
) {
    QINGCHEN("清晨", R.raw.ringtone_qingchen, 1.00f),
    SHUGUANG("风来", R.raw.ringtone_shuguang, 0.70f),
    SHANLAN("钢琴", R.raw.ringtone_shanlan, 0.85f);

    companion object {
        fun fromOrdinal(ordinal: Int): RingtoneOption {
            return values().getOrNull(ordinal) ?: QINGCHEN
        }
    }
}
