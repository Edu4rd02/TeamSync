package com.example.teamsync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.repository.FakeGroupRepository
import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MyGroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<Group> = emptyList()
)

class MyGroupsViewModel(
    groupRepository: GroupRepository = FakeGroupRepository()
) : ViewModel() {

    val uiState: StateFlow<MyGroupsUiState> = groupRepository.getMyGroups()
        .map { MyGroupsUiState(isLoading = false, groups = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MyGroupsUiState()
        )
}
