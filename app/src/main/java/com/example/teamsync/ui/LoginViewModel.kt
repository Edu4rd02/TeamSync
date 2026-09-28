package com.example.teamsync.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.GoogleIdTokenResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    // Check the current sign-in status to initialize state.
    private val _uiState = MutableStateFlow(LoginUiState(isSignedIn = authRepository.isSignedIn))

    // Public uiState to show the value in the UI
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // The context is mutable in case of an activity recreation
    // This doesn't cause memory leaks
    @SuppressLint("StaticFieldLeak")
    private val activityContext = MutableContextWrapper(null)

    fun attachActivity(activity: Context) {
        activityContext.baseContext = activity
    }

    fun detachActivity(activity: Context) {
        if (activityContext.baseContext === activity) activityContext.baseContext = null
    }

    // Function to trigger Google Sign-In flow using the attached activity context
    fun signInWithGoogle() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = authRepository.getGoogleIdToken(activityContext)) {
                is GoogleIdTokenResult.Success -> onGoogleIdTokenReceived(result.idToken)
                GoogleIdTokenResult.Cancelled -> onGoogleSignInCancelled()
                is GoogleIdTokenResult.Failure -> onGoogleSignInFailed(result.cause)
            }
        }
    }

    private suspend fun onGoogleIdTokenReceived(idToken: String) {
        authRepository.signInWithGoogle(idToken)
            .onSuccess { _uiState.update { it.copy(isLoading = false, isSignedIn = true) } }
            .onFailure { e ->
                Log.e(TAG, "Firebase sign-in failed", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't sign in. Check your connection and try again.")
                }
            }
    }

    private fun onGoogleSignInCancelled() {
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun onGoogleSignInFailed(cause: Throwable) {
        Log.e(TAG, "Google sign-in failed", cause)
        _uiState.update {
            it.copy(isLoading = false, errorMessage = "Couldn't sign in with Google. Try again.")
        }
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}
