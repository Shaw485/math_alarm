package com.mira.mathalarm.data

/**
 * 闹钟状态枚举
 * 对应 PRD 中的主状态：未设置 / 生效中 / 本次闹钟已失效 / 响铃中
 */
enum class AlarmState {
    /** 未设置闹钟 */
    NOT_SET,
    /** 闹钟生效中 */
    ACTIVE,
    /** 本次闹钟已失效（用户手动失效） */
    DISABLED,
    /** 响铃中 */
    RINGING
}
