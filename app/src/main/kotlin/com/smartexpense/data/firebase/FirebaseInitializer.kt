package com.smartexpense.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase App / Firestore 초기화.
 * 오프라인 지속성(Persistence)을 켜 Free Tier에서도 로컬 캐시로 장부 입력이 가능합니다.
 */
@Singleton
class FirebaseInitializer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            configureFirestorePersistence()
            initialized = true
        }
    }

    private fun configureFirestorePersistence() {
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        runCatching {
            // Settings는 앱 프로세스당 1회만 설정 가능합니다.
            Firebase.firestore.firestoreSettings = settings
        }
    }

    fun firestore(): FirebaseFirestore {
        ensureInitialized()
        return Firebase.firestore
    }
}
