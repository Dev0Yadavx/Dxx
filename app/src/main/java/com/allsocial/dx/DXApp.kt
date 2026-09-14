package com.allsocial.dx

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class DXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Local Python Backend start karna
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
        } catch (_: Throwable) {}
    }
}
