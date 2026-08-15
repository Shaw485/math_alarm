package com.mira.mathalarm

import android.app.Application
import com.mira.mathalarm.util.AppLogger

class MathAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(applicationContext)
        AppLogger.d(TAG, "MathAlarmApp 启动完成")
    }

    companion object {
        private const val TAG = "MathAlarmApp"
    }
}
