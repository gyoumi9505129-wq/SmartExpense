package com.smartexpense.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var promptActive = false

    fun canAuthenticateWithBiometric(): Boolean {
        val result = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun resetPromptState() {
        promptActive = false
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onNegativeButton: () -> Unit = {},
        onFinished: () -> Unit = {},
        negativeButtonText: String = "취소"
    ): Boolean {
        if (promptActive) {
            onFinished()
            return false
        }
        if (!canAuthenticateWithBiometric()) {
            onError("등록된 생체 인증을 사용할 수 없습니다.")
            onFinished()
            return false
        }

        promptActive = true
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                promptActive = false
                onSuccess()
                onFinished()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                promptActive = false
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onNegativeButton()
                    BiometricPrompt.ERROR_USER_CANCELED -> Unit
                    else -> onError(errString.toString())
                }
                onFinished()
            }

            override fun onAuthenticationFailed() {
                onError("생체 인증에 실패했습니다. 다시 시도해 주세요.")
            }
        }

        return runCatching {
            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("로그인")
                .setSubtitle("지문 또는 얼굴로 인증해 주세요")
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
            prompt.authenticate(info)
            true
        }.getOrElse { error ->
            promptActive = false
            onError("생체 인증을 시작할 수 없습니다. 잠시 후 다시 시도해 주세요.")
            onFinished()
            false
        }
    }
}
