package com.smartexpense.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smartexpense.MainActivity
import com.smartexpense.R
import com.smartexpense.data.model.bank.BankNotificationDraft
import java.util.Locale

object BankDepositNotificationHelper {
    const val CHANNEL_ID = "bank_deposit_parsing"
    const val NOTIFICATION_ID = 4001
    const val ACTION_OPEN_BANK_DRAFT = "com.smartexpense.action.OPEN_BANK_DRAFT"
    const val EXTRA_AMOUNT = "extra_bank_amount"
    const val EXTRA_MEMO = "extra_bank_memo"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "입금 알림 파싱",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "은행 입금 알림을 파싱했을 때 장부·회비 등록 안내"
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, draft: BankNotificationDraft) {
        ensureChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_BANK_DRAFT
            putExtra(EXTRA_AMOUNT, draft.amount)
            putExtra(EXTRA_MEMO, draft.memo)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val amountLabel = "%,d원".format(Locale.KOREA, draft.amount)
        val body = buildString {
            append("$amountLabel 입금이 감지되었습니다.")
            if (draft.memo.isNotBlank()) {
                append("\n메모: ${draft.memo}")
            }
            append("\n탭하면 장부 등록과 회비 자동 매칭을 진행할 수 있습니다.")
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${context.getString(R.string.app_name)} — 입금 알림")
            .setContentText("$amountLabel · 장부·회비 등록")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun restoreDraftFromIntent(intent: Intent?): BankNotificationDraft? {
        if (intent?.action != ACTION_OPEN_BANK_DRAFT) return null
        val amount = intent.getIntExtra(EXTRA_AMOUNT, -1)
        if (amount <= 0) return null
        val memo = intent.getStringExtra(EXTRA_MEMO).orEmpty()
        return BankNotificationDraft(
            amount = amount,
            memo = memo,
            sourcePackage = ""
        )
    }
}
