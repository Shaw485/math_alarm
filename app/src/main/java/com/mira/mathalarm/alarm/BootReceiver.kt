package com.mira.mathalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机广播接收器
 * 接收开机完成、应用更新、时间变更等广播，用于恢复闹钟调度
 * 对应 PRD 6.3 系统时间/时区变更场景
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "收到广播: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 开机或时间变更后，恢复闹钟调度
                val scheduler = AlarmScheduler(context)
                CoroutineScope(Dispatchers.IO).launch {
                    scheduler.restoreAlarmIfNeeded()
                }
            }
        }
    }
}
