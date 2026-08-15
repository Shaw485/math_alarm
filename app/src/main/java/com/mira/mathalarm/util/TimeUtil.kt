package com.mira.mathalarm.util

import java.util.concurrent.TimeUnit

/**
 * 时间格式化工具类
 */
object TimeUtil {

    /**
     * 格式化剩余时间为 "X 小时 X 分钟"
     */
    fun formatRemainingTime(millis: Long): String {
        if (millis <= 0) return "即将响铃"

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours} 小时 ${minutes} 分钟"
            hours > 0 -> "${hours} 小时"
            minutes > 0 -> "${minutes} 分钟"
            else -> "即将响铃"
        }
    }

    /**
     * 格式化时间为 "HH:mm"
     */
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }

    /**
     * 获取当前小时（24小时制）
     */
    fun getCurrentHour(): Int {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    }

    /**
     * 获取当前分钟
     */
    fun getCurrentMinute(): Int {
        return java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
    }
}
