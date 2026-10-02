package com.example.teamsync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MyGroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<Group> = emptyList(),
    val currentUserId: String? = null,
    val photoUrl: String? = null
)

class MyGroupsViewModel(
    private val groupRepository: GroupRepository = GroupRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val groupsFlow: Flow<List<Group>> =
        authRepository.userId
            ?.let { groupRepository.getMyGroups(it) }
            ?: emptyFlow()

    val uiState: StateFlow<MyGroupsUiState> = groupsFlow
        .catch { emit(emptyList()) }
        .map {
            MyGroupsUiState(
                isLoading = false,
                groups = it,
                currentUserId = authRepository.userId,
                photoUrl = authRepository.photoUrl
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MyGroupsUiState()
        )
}
