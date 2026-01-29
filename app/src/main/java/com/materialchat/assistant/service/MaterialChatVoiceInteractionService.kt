package com.materialchat.assistant.service

import android.service.voice.VoiceInteractionService

/**
 * MaterialChat's VoiceInteractionService implementation.
 *
 * This service allows MaterialChat to be set as the default digital assistant
 * on Android devices, replacing Google Gemini or other assistants.
 *
 * Users can activate MaterialChat via:
 * - Long-pressing the power button (Pixel 6+)
 * - Corner swipe gesture (Pixel 4+)
 * - "Hey Google" (requires Google integration, not implemented)
 *
 * This service is registered in AndroidManifest.xml with the
 * android.permission.BIND_VOICE_INTERACTION permission.
 */
class MaterialChatVoiceInteractionService : VoiceInteractionService() {

    override fun onCreate() {
        super.onCreate()
        // Service created - the session service will handle actual interactions
    }

    override fun onReady() {
        super.onReady()
        // Service is ready to handle voice interactions
    }

    override fun onShutdown() {
        super.onShutdown()
        // Clean up any resources
    }
}
