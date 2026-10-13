package com.example.teamsync.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isSigningOut: Boolean = false,
    val isSignedOut: Boolean = false
)

class ProfileViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            displayName = authRepository.displayName,
            email = authRepository.email,
            photoUrl = authRepository.photoUrl
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun signOut(context: Context) {
        if (_uiState.value.isSigningOut) return // guards double taps
        _uiState.update { it.copy(isSigningOut = true) }
        // Application context: safe to hold across rotation while the coroutine runs.
        val appContext = context.applicationContext

        viewModelScope.launch {
            authRepository.userId?.let { uid ->
                runCatching { userRepository.removeToken(uid) }
                    .onFailure { Log.e("ProfileViewModel", "removeToken failed",it) }
            }
            authRepository.signOut(appContext)
            _uiState.update { it.copy(isSigningOut = false, isSignedOut = true) }
        }
    }
}
