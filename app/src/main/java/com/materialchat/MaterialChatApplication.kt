package com.materialchat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for MaterialChat.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MaterialChatApplication : Application()
