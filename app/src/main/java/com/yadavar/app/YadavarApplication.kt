package com.yadavar.app

import android.app.Application
import com.yadavar.app.ads.YadavarAds

class YadavarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        YadavarAds.initialize(this)
    }
}
