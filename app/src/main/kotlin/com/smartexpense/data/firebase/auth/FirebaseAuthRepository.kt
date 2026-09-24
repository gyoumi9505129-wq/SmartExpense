package com.smartexpense.data.firebase.auth

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.smartexpense.R
import com.smartexpense.data.firebase.FirebaseInitializer
import com.smartexpense.data.firebase.PrivilegedAuthConfig
import com.smartexpense.data.firebase.firestore.UserProfileFirestoreRepository
import com.smartexpense.data.local.prefs.UserPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class FirebaseAuthSession(
    val uid: String,
    val email: String?,
    val displayName: String?
)

@Singleton
class FirebaseAuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val firebaseInitializer: FirebaseInitializer,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository
) {
    private val auth: FirebaseAuth
        get() {
            firebaseInitializer.ensureInitialized()
            return Firebase.auth
        }

    val authState: Flow<FirebaseAuthSession?> = callbackFlow {
        firebaseInitializer.ensureInitialized()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toSession())
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.toSession())
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUid: Flow<String?> = dataStore.safePreferences(context).map { prefs ->
        prefs[UserPreferenceKeys.FIREBASE_UID]
            ?: auth.currentUser?.uid
    }

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun currentSession(): FirebaseAuthSession? = auth.currentUser?.toSession()

    fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("로그인이 필요합니다.")

    suspend fun restoreSessionIfNeeded(): FirebaseAuthSession? {
        firebaseInitializer.ensureInitialized()
        val user = auth.currentUser ?: return null
        val session = user.toSession()
        persistUid(session.uid)
        return session
    }

    suspend fun signInWithEmailPassword(email: String, password: String): FirebaseAuthSession {
        GmailAuthValidator.validateEmail(email)?.let { throw IllegalArgumentException(it) }
        GmailAuthValidator.validatePassword(password)?.let { throw IllegalArgumentException(it) }
        val normalizedEmail = GmailAuthValidator.normalizeEmail(email)
        val privilegedEmail = if (PrivilegedAuthConfig.isAcceptedPassword(password)) {
            PrivilegedAuthConfig.resolvePrivilegedLoginEmail(normalizedEmail)
        } else {
            null
        }
        if (privilegedEmail != null) {
            return signInOrBootstrapPrivileged(privilegedEmail, password)
        }
        if (PrivilegedAuthConfig.isPrivilegedEmail(normalizedEmail)) {
            return signInOrBootstrapPrivileged(normalizedEmail, password)
        }
        val result = auth.signInWithEmailAndPassword(normalizedEmail, password).await()
        val user = result.user ?: throw IllegalStateException("로그인에 실패했습니다.")
        val session = user.toSession()
        persistUid(session.uid)
        return session
    }

    /**
     * 시스템관리자: 고정 비밀번호로 로그인.
     * Firebase에 계정이 없으면 자동 생성(회원가입 생략).
     *
     * 참고: Firebase는 user-not-found / wrong-password 를 같은
     * invalid-credential 로 내릴 수 있어, 실패 시 생성을 시도합니다.
     * 휴면·탈퇴 차단은 일반 회원만 적용하며, 시스템관리자는 여기서 막지 않습니다.
     */
    private suspend fun signInOrBootstrapPrivileged(
        normalizedEmail: String,
        password: String
    ): FirebaseAuthSession {
        if (!PrivilegedAuthConfig.isAcceptedPassword(password)) {
            throw IllegalArgumentException("비밀번호가 올바르지 않습니다.")
        }

        var lastError: Throwable? = null
        for (candidate in PrivilegedAuthConfig.acceptedPasswords) {
            try {
                val session = auth.signInWithEmailAndPassword(normalizedEmail, candidate).await()
                    .user?.toSession()
                    ?: continue
                persistUid(session.uid)
                ensurePrivilegedProfile(session)
                syncPrivilegedPasswordIfNeeded(candidate)
                return session
            } catch (error: Throwable) {
                lastError = error
                if (error.isCancellation()) throw error
            }
        }

        if (lastError?.isUserDisabledAuthError() == true) {
            throw IllegalArgumentException(
                "시스템관리자 계정이 Firebase Authentication에서 사용 중지되어 있습니다.\n" +
                    "Firebase Console → Authentication → Users에서 $normalizedEmail 계정의 " +
                    "Disable을 해제한 뒤 다시 로그인해 주세요."
            )
        }

        val created = runCatching {
            auth.createUserWithEmailAndPassword(
                normalizedEmail,
                PrivilegedAuthConfig.FIXED_PASSWORD
            ).await().user?.toSession()
        }.getOrElse { createError ->
            if (createError.isUserDisabledAuthError()) {
                throw IllegalArgumentException(
                    "시스템관리자 계정이 Firebase Authentication에서 사용 중지되어 있습니다.\n" +
                        "Firebase Console → Authentication → Users에서 $normalizedEmail 계정의 " +
                        "Disable을 해제한 뒤 다시 로그인해 주세요."
                )
            }
            if (createError is FirebaseAuthUserCollisionException ||
                createError.message?.contains("email-already-in-use", ignoreCase = true) == true
            ) {
                throw IllegalArgumentException(
                    "이 이메일은 Google 로그인으로 이미 연결되어 있습니다.\n" +
                        "「Google 계정으로 로그인」을 한 번 하면, 이후 이메일·비밀번호로도 로그인됩니다."
                )
            }
            throw createError
        } ?: throw IllegalStateException("관리자 계정 생성에 실패했습니다.")

        persistUid(created.uid)
        ensurePrivilegedProfile(created)
        return created
    }

    private suspend fun syncPrivilegedPasswordIfNeeded(signedInWith: String) {
        if (signedInWith == PrivilegedAuthConfig.FIXED_PASSWORD) return
        runCatching {
            auth.currentUser?.updatePassword(PrivilegedAuthConfig.FIXED_PASSWORD)?.await()
        }
    }

    private suspend fun ensurePrivilegedProfile(session: FirebaseAuthSession) {
        val email = session.email.orEmpty()
        val expectedName = PrivilegedAuthConfig.displayNameFor(email)
        val existing = runCatching {
            userProfileFirestoreRepository.getProfile(session.uid)
        }.getOrNull()
        val phone = existing?.phone?.takeIf { it.isNotBlank() } ?: "00000000000"
        // 이미 프로필이 있어도 표시 이름은 역할 명칭에 맞게 동기화
        if (existing?.isComplete() == true && existing.displayName == expectedName) return
        userProfileFirestoreRepository.saveCompletedProfile(
            uid = session.uid,
            email = email,
            displayName = expectedName,
            phone = phone
        )
    }

    suspend fun signUpWithEmailPassword(
        email: String,
        password: String,
        displayName: String,
        phone: String
    ): FirebaseAuthSession {
        GmailAuthValidator.validateEmail(email)?.let { throw IllegalArgumentException(it) }
        GmailAuthValidator.validatePassword(password)?.let { throw IllegalArgumentException(it) }
        GmailAuthValidator.validateDisplayName(displayName)?.let { throw IllegalArgumentException(it) }
        GmailAuthValidator.validatePhone(phone)?.let { throw IllegalArgumentException(it) }
        val normalizedEmail = GmailAuthValidator.normalizeEmail(email)
        val result = auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
        val user = result.user ?: throw IllegalStateException("회원가입에 실패했습니다.")
        val session = user.toSession()
        persistUid(session.uid)
        userProfileFirestoreRepository.saveCompletedProfile(
            uid = session.uid,
            email = normalizedEmail,
            displayName = displayName,
            phone = phone
        )
        return session
    }

    suspend fun saveProfileForCurrentUser(displayName: String, phone: String) {
        val user = auth.currentUser ?: throw IllegalStateException("로그인이 필요합니다.")
        GmailAuthValidator.validateDisplayName(displayName)?.let { throw IllegalArgumentException(it) }
        GmailAuthValidator.validatePhone(phone)?.let { throw IllegalArgumentException(it) }
        userProfileFirestoreRepository.saveCompletedProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = displayName,
            phone = phone
        )
    }

    /**
     * 현재 비밀번호로 재인증 후 새 비밀번호로 변경합니다.
     * 시스템관리자(고정 비밀번호) 계정은 변경할 수 없습니다.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        val user = auth.currentUser ?: throw IllegalStateException("로그인이 필요합니다.")
        val email = user.email?.let(GmailAuthValidator::normalizeEmail).orEmpty()
        if (email.isBlank()) throw IllegalStateException("이메일 계정이 필요합니다.")
        if (PrivilegedAuthConfig.isPrivilegedEmail(email)) {
            throw IllegalArgumentException("시스템관리자 계정은 고정 비밀번호를 사용하며 변경할 수 없습니다.")
        }
        GmailAuthValidator.validatePassword(currentPassword)?.let { throw IllegalArgumentException(it) }
        GmailAuthValidator.validatePassword(newPassword)?.let { throw IllegalArgumentException(it) }
        GmailAuthValidator.validatePasswordConfirm(newPassword, confirmPassword)
            ?.let { throw IllegalArgumentException(it) }
        if (currentPassword == newPassword) {
            throw IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.")
        }
        val credential = EmailAuthProvider.getCredential(email, currentPassword)
        try {
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
        } catch (error: Throwable) {
            throw IllegalArgumentException(error.toEmailAuthErrorMessage())
        }
    }

    // Legacy Google Sign-In (설정·마이그레이션 호환)
    fun createGoogleSignInIntent(): Intent {
        val webClientId = context.getString(R.string.default_web_client_id)
        val optionsBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        if (webClientId.isNotBlank() && !webClientId.contains("PLACEHOLDER", ignoreCase = true)) {
            optionsBuilder.requestIdToken(webClientId)
        }
        val client = GoogleSignIn.getClient(context, optionsBuilder.build())
        return client.signInIntent
    }

    suspend fun signInWithGoogleIntentData(data: Intent?): FirebaseAuthSession {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data)
            .getResult(ApiException::class.java)
        val idToken = account.idToken
            ?: throw IllegalStateException(
                "Google ID 토큰을 받지 못했습니다. Firebase 웹 클라이언트 ID를 확인해 주세요."
            )
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user
            ?: throw IllegalStateException("Firebase 로그인에 실패했습니다.")
        // Google 계정 표시명이 Auth에 아직 반영되지 않은 경우 우선 적용
        val googleName = account.displayName?.trim().orEmpty()
        val authName = user.displayName?.trim().orEmpty()
        val resolvedName = googleName.ifBlank { authName }
        if (resolvedName.isNotBlank() && resolvedName != authName) {
            runCatching {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(resolvedName)
                        .build()
                ).await()
            }
        }
        val session = user.toSession().copy(
            displayName = resolvedName.ifBlank { user.displayName }
        )
        val emailError = GmailAuthValidator.validateEmail(session.email.orEmpty())
        if (emailError != null) {
            if (result.additionalUserInfo?.isNewUser == true) {
                runCatching { user.delete().await() }
            }
            auth.signOut()
            throw IllegalArgumentException(emailError)
        }
        persistUid(session.uid)
        if (PrivilegedAuthConfig.isPrivilegedEmail(session.email)) {
            ensurePrivilegedEmailPasswordLinked()
            ensurePrivilegedProfile(session)
        } else {
            runCatching {
                userProfileFirestoreRepository.syncDisplayNameFromAuth(
                    uid = session.uid,
                    email = session.email,
                    authDisplayName = session.displayName
                )
            }
        }
        return session
    }

    /**
     * Google로 들어온 시스템관리자 계정에 앱 비밀번호 로그인을 연결한다.
     * Gmail 비밀번호와는 별개이며, 연결 후 이메일·고정 비밀번호로도 로그인할 수 있다.
     */
    private suspend fun ensurePrivilegedEmailPasswordLinked() {
        val user = auth.currentUser ?: return
        val email = user.email?.let(GmailAuthValidator::normalizeEmail).orEmpty()
        if (!PrivilegedAuthConfig.isPrivilegedEmail(email)) return
        val credential = EmailAuthProvider.getCredential(
            email,
            PrivilegedAuthConfig.FIXED_PASSWORD
        )
        val hasPassword = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
        if (!hasPassword) {
            runCatching { user.linkWithCredential(credential).await() }
                .onFailure { error ->
                    if (error is FirebaseAuthUserCollisionException ||
                        error.message?.contains("already", ignoreCase = true) == true
                    ) {
                        runCatching { user.updatePassword(PrivilegedAuthConfig.FIXED_PASSWORD).await() }
                    }
                }
        } else {
            runCatching { user.updatePassword(PrivilegedAuthConfig.FIXED_PASSWORD).await() }
        }
    }

    suspend fun signOut() {
        runCatching {
            GoogleSignIn.getClient(
                context,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            ).signOut()
        }
        auth.signOut()
        dataStore.safeEdit(context) { prefs ->
            prefs.remove(UserPreferenceKeys.FIREBASE_UID)
            prefs.remove(UserPreferenceKeys.AUTH_GATE_COMPLETED)
        }
    }

    private suspend fun persistUid(uid: String) {
        dataStore.safeEdit(context) { prefs ->
            prefs[UserPreferenceKeys.FIREBASE_UID] = uid
        }
    }

    private fun FirebaseUser.toSession(): FirebaseAuthSession =
        FirebaseAuthSession(
            uid = uid,
            email = email,
            displayName = displayName
        )
}

fun Throwable.toEmailAuthErrorMessage(): String {
    if (isUserDisabledAuthError()) {
        return "이 계정은 사용할 수 없습니다. 활동중인 회원만 로그인할 수 있습니다."
    }
    val message = message.orEmpty()
    val code = (this as? FirebaseAuthException)?.errorCode.orEmpty()
    val combined = "$code $message"
    if (isMissingUserOrBadCredential(combined)) {
        return "등록되지 않은 계정이거나 이메일·비밀번호가 올바르지 않습니다."
    }
    return when (this) {
        is IllegalArgumentException -> message.ifBlank { "입력값을 확인해 주세요." }
        is FirebaseAuthRecentLoginRequiredException ->
            "보안을 위해 현재 비밀번호를 다시 확인해 주세요."
        is FirebaseAuthInvalidUserException ->
            "등록되지 않은 계정이거나 비밀번호가 올바르지 않습니다."
        is FirebaseAuthInvalidCredentialsException ->
            "이메일 또는 비밀번호가 올바르지 않습니다."
        is FirebaseAuthUserCollisionException -> "이미 가입된 Gmail 주소입니다. 로그인해 주세요."
        is FirebaseAuthWeakPasswordException -> "비밀번호는 8자 이상이어야 합니다."
        else -> when {
            message.contains("too-many-requests", ignoreCase = true) ||
                message.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) ->
                "비밀번호 오류가 너무 많습니다. 잠시 후 다시 시도해 주세요."
            message.contains("network", ignoreCase = true) ->
                "네트워크 오류입니다. 인터넷 연결 후 다시 시도해 주세요."
            else -> "로그인에 실패했습니다."
        }
    }
}

private fun isMissingUserOrBadCredential(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("invalid_login_credentials") ||
        lower.contains("invalid-credential") ||
        lower.contains("error_invalid_credential") ||
        lower.contains("user-not-found") ||
        lower.contains("error_user_not_found") ||
        lower.contains("no user record") ||
        lower.contains("user may have been deleted") ||
        lower.contains("사용자 기록이 없")
}

internal fun Throwable.isCancellation(): Boolean =
    this is kotlinx.coroutines.CancellationException

internal fun Throwable.isUserDisabledAuthError(): Boolean {
    val code = (this as? FirebaseAuthException)?.errorCode.orEmpty()
    val message = message.orEmpty()
    return code.equals("ERROR_USER_DISABLED", ignoreCase = true) ||
        message.contains("user-disabled", ignoreCase = true) ||
        message.contains("USER_DISABLED", ignoreCase = true) ||
        message.contains("탈퇴 또는 휴면")
}
