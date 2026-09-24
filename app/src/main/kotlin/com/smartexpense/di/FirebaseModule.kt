package com.smartexpense.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.smartexpense.data.firebase.FirebaseInitializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(initializer: FirebaseInitializer): FirebaseAuth {
        initializer.ensureInitialized()
        return Firebase.auth
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(initializer: FirebaseInitializer): FirebaseFirestore =
        initializer.firestore()
}
