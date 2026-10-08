package com.example.teamsync.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.teamsync.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

// A window of time in which the whole group (or most of it) is free.
data class FreeBlock(
    val start: LocalTime,
    val end: LocalTime
)

data class DayAvailability(
    val date: LocalDate,
    val freeCount: Int,
    val totalCount: Int,
    val blocks: List<FreeBlock>,
    val busyNote: String? = null
) {
    val isEveryoneFree: Boolean get() = freeCount == totalCount
}

data class BlockSelection(
    val date: LocalDate,
    val block: FreeBlock,
    val startTime: LocalTime
)

data class CreateEventUiState(
    val photoUrl: String? = null,
    val duration: Duration = Duration.ofHours(1),
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now().plusDays(7),
    val lastSyncedMinutesAgo: Int? = null,
    val days: List<DayAvailability> = emptyList(),
    val selection: BlockSelection? = null
) {
    // The latest start that still fits the event inside the selected block.
    val latestStart: LocalTime?
        get() = selection?.block?.end?.minus(duration)

    val canConfirm: Boolean
        get() = selection != null && latestStart?.let { selection.startTime <= it } == true
}

class CreateEventViewModel(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(CreateEventUiState(photoUrl = authRepository.photoUrl))
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    // A new duration or range can invalidate the chosen block, so the selection is reset.
    fun onDurationChange(duration: Duration) = _uiState.update {
        it.copy(duration = duration, selection = null)
    }

    fun onDateRangeChange(start: LocalDate, end: LocalDate) = _uiState.update {
        it.copy(rangeStart = start, rangeEnd = end, selection = null)
    }

    fun onBlockSelected(date: LocalDate, block: FreeBlock) = _uiState.update {
        it.copy(selection = BlockSelection(date = date, block = block, startTime = block.start))
    }

    fun onStartTimeChange(time: LocalTime) = _uiState.update { state ->
        val selection = state.selection ?: return@update state
        // Keep the start time inside the selected block.
        val coerced = time.coerceIn(selection.block.start, state.latestStart ?: selection.block.start)
        state.copy(selection = selection.copy(startTime = coerced))
    }
}
