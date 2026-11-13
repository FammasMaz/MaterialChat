package com.materialchat.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs after the app is updated, which is when Android drops its assistant binding.
 *
 * Without this the assistant stays dead until the user switches to another assistant and
 * back, because the platform sees the same component name as before and keeps its stale
 * binding.
 */
class AssistantRebindReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AssistantRebinder(appContext, AssistantBindingDetector(appContext)).rebindIfNeeded()
            } finally {
                pending.finish()
            }
        }
    }
}
