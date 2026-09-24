package com.smartexpense.util

import android.content.Context
import android.content.Intent
import com.smartexpense.ui.club.dues.MemberDuesStatusRowUi
import java.util.Locale

object DuesReminderMessenger {
    private const val KAKAO_TALK_PACKAGE = "com.kakao.talk"

    fun buildMessage(row: MemberDuesStatusRowUi, clubName: String): String {
        val unpaidDetails = row.unpaidDetails.ifBlank { "미납" }
        return "[$clubName 회비 안내] ${row.memberName}님, $unpaidDetails 회비 총 ${
            "%,d".format(Locale.KOREA, row.totalUnpaidAmount)
        }원이 미납되었습니다. 확인 부탁드립니다."
    }

    fun sendViaKakaoTalk(context: Context, message: String): Boolean {
        val kakaoIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage(KAKAO_TALK_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(kakaoIntent)
            true
        }.getOrElse {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallback, "회비 안내 보내기"))
            false
        }
    }
}
