package com.materialchat

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.materialchat.di.ApplicationScope
import com.materialchat.domain.repository.ConversationRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main Application class for MaterialChat.
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 */
@HiltAndroidApp
class MaterialChatApplication : Application() {

    @Inject
    lateinit var conversationRepository: ConversationRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.ADS_ENABLED) {
            MobileAds.initialize(this)
        }
        clearInterruptedStreams()
    }

    /**
     * A crash or process death mid-stream leaves messages marked as streaming,
     * which shows a permanent typing indicator and a "streaming" badge in the
     * conversation list until that specific chat is reopened. Reset them once
     * per process, before any new stream can start.
     */
    private fun clearInterruptedStreams() {
        applicationScope.launch {
            runCatching { conversationRepository.clearInterruptedStreamingFlags() }
        }
    }
}
