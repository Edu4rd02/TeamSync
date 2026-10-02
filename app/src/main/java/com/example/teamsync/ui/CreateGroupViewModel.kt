package com.example.teamsync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException

private const val CREATE_TIMEOUT_MS = 10_000L
private const val CREATE_ERROR = "Couldn't create the group. Check your connection and try again."

data class CreateGroupUiState(
    val name: String = "",
    val description: String = "",
    val workDays: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet(),
    val workStart: LocalTime = LocalTime.of(7, 0),
    val workEnd: LocalTime = LocalTime.of(17, 0),
    val photoUrl: String? = null,
    val isCreating: Boolean = false,
    val isCreated: Boolean = false,
    val error: String? = null
) {
    val canCreate: Boolean
        get() = name.isNotBlank() && workDays.isNotEmpty() && workStart < workEnd && !isCreating
}

class CreateGroupViewModel(
    private val groupRepository: GroupRepository = GroupRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState(photoUrl = authRepository.photoUrl))
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name) }

    fun onDescriptionChange(description: String) = _uiState.update { it.copy(description = description) }

    fun onWorkDayToggle(day: DayOfWeek) = _uiState.update {
        it.copy(workDays = if (day in it.workDays) it.workDays - day else it.workDays + day)
    }

    fun onWorkStartChange(time: LocalTime) = _uiState.update { it.copy(workStart = time) }

    fun onWorkEndChange(time: LocalTime) = _uiState.update { it.copy(workEnd = time) }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun createGroup() {
        val state = _uiState.value
        if (!state.canCreate) return
        val ownerId = authRepository.userId ?: return
        _uiState.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            try {
                // Timeout in case if the user doesn't have internet connection
                withTimeout(CREATE_TIMEOUT_MS) {
                    groupRepository.createGroup(
                        ownerId = ownerId,
                        ownerDisplayName = authRepository.displayName,
                        ownerPhotoUrl = authRepository.photoUrl,
                        name = state.name.trim(),
                        description = state.description.trim().ifEmpty { null },
                        workDays = state.workDays.sorted(),
                        workStart = state.workStart,
                        workEnd = state.workEnd
                    )
                }
                _uiState.update { it.copy(isCreating = false, isCreated = true) }
            } catch (e: TimeoutCancellationException) {
                _uiState.update { it.copy(isCreating = false, error = CREATE_ERROR) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreating = false, error = CREATE_ERROR) }
            }
        }
    }
}
