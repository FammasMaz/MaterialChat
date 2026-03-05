package com.materialchat.notifications

import com.materialchat.data.local.preferences.AppPreferences
import com.materialchat.domain.usecase.openclaw.ConnectGatewayUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawConfigUseCase
import com.materialchat.domain.usecase.openclaw.ManageOpenClawSessionsUseCase
import com.materialchat.domain.usecase.openclaw.OpenClawChatUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OpenClawNotificationEntryPoint {
    fun appPreferences(): AppPreferences
    fun connectGatewayUseCase(): ConnectGatewayUseCase
    fun manageOpenClawConfigUseCase(): ManageOpenClawConfigUseCase
    fun manageOpenClawSessionsUseCase(): ManageOpenClawSessionsUseCase
    fun openClawChatUseCase(): OpenClawChatUseCase
    fun appNotificationManager(): AppNotificationManager
}
