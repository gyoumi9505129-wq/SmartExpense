package com.smartexpense

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.smartexpense.di.BankNotificationEntryPoint
import com.smartexpense.ui.SmartExpenseRoot
import com.smartexpense.ui.theme.SmartExpenseTheme
import com.smartexpense.util.AppConstants
import com.smartexpense.util.BankDepositNotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var keepSplashScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        enableEdgeToEdge()
        setContent {
            SmartExpenseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartExpenseRoot(
                        activity = this,
                        onAuthGateResolved = { keepSplashScreen = false }
                    )
                }
            }
        }
        // Safety timeout: never leave splash forever
        lifecycleScope.launch {
            delay(4_000)
            keepSplashScreen = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        handleBankNotificationIntent(intent)
        if (intent?.action == AppConstants.ACTION_OPEN_TRANSACTION_FORM) {
            EntryPointAccessors.fromApplication(
                applicationContext,
                BankNotificationEntryPoint::class.java
            ).bankNotificationCoordinator().requestOpenEmptyTransactionForm()
        }
    }

    private fun handleBankNotificationIntent(intent: Intent?) {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BankNotificationEntryPoint::class.java
        )
        val draftFromIntent = BankDepositNotificationHelper.restoreDraftFromIntent(intent)
        if (draftFromIntent != null) {
            val draftRepository = entryPoint.bankNotificationDraftRepository()
            if (draftRepository.peek() == null) {
                draftRepository.publish(draftFromIntent)
            }
            entryPoint.bankNotificationCoordinator().requestOpenTransactionFormFromBankNotification()
        }
    }
}
