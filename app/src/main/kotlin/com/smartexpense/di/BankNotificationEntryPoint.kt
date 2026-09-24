package com.smartexpense.di

import com.smartexpense.data.repository.bank.BankNotificationCoordinator
import com.smartexpense.data.repository.bank.BankNotificationDraftRepository
import com.smartexpense.data.repository.bank.BankNotificationSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BankNotificationEntryPoint {
    fun bankNotificationDraftRepository(): BankNotificationDraftRepository
    fun bankNotificationSettingsRepository(): BankNotificationSettingsRepository
    fun bankNotificationCoordinator(): BankNotificationCoordinator
}
