package com.materialchat

import android.app.Application
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for MaterialChat.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MaterialChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.ADS_ENABLED) {
            MobileAds.initialize(this)
        }
    }
}
