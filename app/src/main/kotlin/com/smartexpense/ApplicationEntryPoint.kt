package com.smartexpense

import com.smartexpense.data.firebase.FirebaseInitializer
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.session.AuthSessionManager
import com.smartexpense.data.session.IdleSessionManager
import com.smartexpense.data.session.UserSessionManager
import com.smartexpense.ui.startup.AppStartManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApplicationEntryPoint {
    fun appStartManager(): AppStartManager
    fun authSessionManager(): AuthSessionManager
    fun authRefreshGuard(): AuthRefreshGuard
    fun userSessionManager(): UserSessionManager
    fun idleSessionManager(): IdleSessionManager
    fun firebaseInitializer(): FirebaseInitializer
}
