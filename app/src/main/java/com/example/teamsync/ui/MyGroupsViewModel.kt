package com.example.teamsync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyGroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<Group> = emptyList(),
    val currentUserId: String? = null,
    val photoUrl: String? = null,
    val isJoining: Boolean = false,
    val joinError: String? = null
)

class MyGroupsViewModel(
    private val groupRepository: GroupRepository = GroupRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyGroupsUiState(photoUrl = authRepository.photoUrl))
    val uiState: StateFlow<MyGroupsUiState> = _uiState.asStateFlow()

    init {
        val userId = authRepository.userId
        if (userId == null) {
            _uiState.update { it.copy(isLoading = false) }
        } else {
            viewModelScope.launch {
                groupRepository.getMyGroups(userId)
                    .catch { _uiState.update { state -> state.copy(isLoading = false) } }
                    .collect { groups ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                groups = groups,
                                currentUserId = userId
                            )
                        }
                    }
            }
        }
    }

    fun onJoinErrorDismissed() = _uiState.update { it.copy(joinError = null) }

    suspend fun joinGroup(joinCode: String): Boolean {
        val userId = authRepository.userId ?: return false
        val code = joinCode.uppercase().trim()
        if (code.length != 6) {
            _uiState.update { it.copy(joinError = "Enter a valid group code") }
            return false
        }
        _uiState.update { it.copy(isJoining = true, joinError = null) }
        val result = groupRepository.joinGroup(
            invitationCode = code,
            userId = userId,
            displayName = authRepository.displayName,
            photoUrl = authRepository.photoUrl
        )
        val error = result.exceptionOrNull()?.let { e ->
            if (e is NoSuchElementException) "There are not groups with this code"
            else "Something goes wrong, try later."
        }
        _uiState.update { it.copy(isJoining = false, joinError = error) }
        return error == null
    }
}
