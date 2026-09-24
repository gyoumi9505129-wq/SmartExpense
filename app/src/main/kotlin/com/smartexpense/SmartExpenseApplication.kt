package com.smartexpense

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.smartexpense.data.firebase.FirebaseInitializer
import com.smartexpense.data.local.ClubDatabaseResetHelper
import com.smartexpense.data.session.IdleSessionManager
import com.smartexpense.ui.startup.AppStartManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmartExpenseApplication : Application() {

    @Inject
    lateinit var firebaseInitializer: FirebaseInitializer

    @Inject
    lateinit var appStartManager: AppStartManager

    @Inject
    lateinit var idleSessionManager: IdleSessionManager

    override fun onCreate() {
        runCatching { ClubDatabaseResetHelper.resetIfIncompatible(this) }
        super.onCreate()
        runCatching { firebaseInitializer.ensureInitialized() }
        runCatching { appStartManager.onColdStart() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    idleSessionManager.onAppForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    idleSessionManager.onAppBackground()
                }
            }
        )
    }
}
