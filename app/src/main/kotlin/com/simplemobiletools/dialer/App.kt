package com.simplemobiletools.dialer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.simplemobiletools.commons.extensions.checkUseEnglish
import com.simplemobiletools.dialer.activities.MainActivity

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
    }
}
