package com.smartexpense.service.bank

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.smartexpense.di.BankNotificationEntryPoint
import com.smartexpense.util.BankDepositNotificationHelper
import dagger.hilt.android.EntryPointAccessors

class BankNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing || sbn.isGroup) return

        val packageName = sbn.packageName ?: return

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BankNotificationEntryPoint::class.java
        )
        if (!entryPoint.bankNotificationSettingsRepository().isPackageEnabled(packageName)) return

        val text = extractNotificationText(sbn) ?: return
        val settingsRepository = entryPoint.bankNotificationSettingsRepository()
        val parsed = BankNotificationParser.parse(
            text = text,
            packageName = packageName,
            appLabel = settingsRepository.labelForPackage(packageName)
        ) ?: return

        entryPoint.bankNotificationDraftRepository().publish(parsed)

        val isAppInForeground = ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
        entryPoint.bankNotificationCoordinator().requestOpenTransactionFormFromBankNotification()
        if (!isAppInForeground) {
            BankDepositNotificationHelper.show(applicationContext, parsed)
        }
    }

    private fun extractNotificationText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val lines = extras.getCharSequenceArray("android.textLines")
            ?.joinToString(" ") { it.toString() }
            .orEmpty()

        return listOf(title, text, bigText, lines)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .ifBlank { null }
    }
}
