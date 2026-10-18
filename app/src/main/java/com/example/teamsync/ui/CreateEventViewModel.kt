package com.example.teamsync.ui

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.CalendarSync
import com.example.teamsync.data.MeetingOption
import com.example.teamsync.data.isFree
import com.example.teamsync.data.model.TimeSlot
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.EventRepository
import com.google.firebase.Timestamp
import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// A slot of time in which the whole group (or most of it) is free, with who can and can't make it.
data class FreeBlock(
    val start: LocalTime,
    val end: LocalTime,
    val attendees: List<String> = emptyList(),
    val missing: List<String> = emptyList()
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

enum class HoursError { START_AFTER_END, OUTSIDE_WORK_HOURS, SHORTER_THAN_DURATION }

data class CreateEventUiState(
    val title: String = "",
    val link: String = "",
    val photoUrl: String? = null,
    val duration: Duration = Duration.ofHours(1),
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now().plusDays(7),
    // The group's work hours
    val workStart: LocalTime? = null,
    val workEnd: LocalTime? = null,
    // Preferred hours of the day for the event; null means no preference.
    val preferredStart: LocalTime? = null,
    val preferredEnd: LocalTime? = null,
    val lastSyncedMinutesAgo: Int? = null,
    val days: List<DayAvailability> = emptyList(),
    // Members whose calendar isn't synced: they aren't counted in the availability.
    val unsyncedCount: Int = 0,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val selection: BlockSelection? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    // Field errors are shown only after the user tried to confirm.
    val showErrors: Boolean = false,
    // Editing an existing event: picking a block is optional (without one the time doesn't change)
    val isEditing: Boolean = false,
    val currentStart: LocalDateTime? = null,
    val currentEnd: LocalTime? = null
) {
    val isTitleValid: Boolean get() = title.isNotBlank()

    val isLinkValid: Boolean
        get() = link.isBlank() || Patterns.WEB_URL.matcher(link.trim()).matches()

    val hoursError: HoursError?
        get() {
            val start = preferredStart ?: return null
            val end = preferredEnd ?: return null
            return when {
                start > end -> HoursError.START_AFTER_END
                (workStart != null && start < workStart) || (workEnd != null && end > workEnd) ->
                    HoursError.OUTSIDE_WORK_HOURS
                Duration.between(start, end) < duration -> HoursError.SHORTER_THAN_DURATION
                else -> null
            }
        }

    // The hours to pass to the algorithm, only when they are set and valid.
    val preferredHours: ClosedRange<LocalTime>?
        get() = if (preferredStart != null && preferredEnd != null && hoursError == null) {
            preferredStart..preferredEnd
        } else null

    // The latest start that still fits the event inside the selected block.
    val latestStart: LocalTime?
        get() = selection?.block?.end?.minus(duration)

    val canConfirm: Boolean
        get() = if (selection == null) isEditing
        else latestStart?.let { selection.startTime <= it } == true
}

class CreateEventViewModel(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository = AuthRepository(),
    private val groupRepository: GroupRepository = GroupRepository(),
    private val eventRepository: EventRepository = EventRepository()
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])

    // Present when editing an existing event
    private val eventId: String? = savedStateHandle["eventId"]

    private val _uiState = MutableStateFlow(
        CreateEventUiState(photoUrl = authRepository.photoUrl, isEditing = eventId != null)
    )
    val uiState: StateFlow<CreateEventUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    // Busy time of the synced members from the last search, to know who is free at the chosen start
    private var busyByUser: Map<String, List<TimeSlot>> = emptyMap()

    init {
        // Get the work window
        viewModelScope.launch {
            groupRepository.getGroupOnce(groupId)?.let { group ->
                _uiState.update { it.copy(workStart = group.workStart, workEnd = group.workEnd) }
            }
        }
        if (eventId != null) loadEvent(eventId) else refreshBlocks()
    }

    // Fills the form with the event being edited; its duration is used to look for new blocks
    private fun loadEvent(eventId: String) {
        viewModelScope.launch {
            try {
                eventRepository.getEventOnce(groupId, eventId)?.let { event ->
                    val zone = ZoneId.systemDefault()
                    _uiState.update {
                        it.copy(
                            title = event.title,
                            link = event.link.orEmpty(),
                            duration = Duration.between(event.startAt, event.endAt),
                            currentStart = event.startAt.atZone(zone).toLocalDateTime(),
                            currentEnd = event.endAt.atZone(zone).toLocalTime()
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CreateEventViewModel", "Could not load event $eventId", e)
            }
            refreshBlocks()
        }
    }

    // A new duration or range can invalidate the chosen block, so the selection is reset.
    fun onDurationChange(duration: Duration) {
        _uiState.update { it.copy(duration = duration, selection = null) }
        refreshBlocks()
    }

    // Only the days that the calendar sync covers (today up to SYNC_DAYS) can be picked.
    fun onDateRangeChange(start: LocalDate, end: LocalDate) {
        _uiState.update {
            val first = LocalDate.now()
            val last = first.plusDays(CalendarSync.SYNC_DAYS - 1L)
            val coercedStart = start.coerceIn(first, last)
            it.copy(rangeStart = coercedStart, rangeEnd = end.coerceIn(coercedStart, last), selection = null)
        }
        refreshBlocks()
    }

    //
    // Picking the whole work day is the same as having no preference.
    fun onPreferredHoursChange(start: LocalTime, end: LocalTime) {
        _uiState.update {
            val isWholeDay = start == it.workStart && end == it.workEnd
            it.copy(
                preferredStart = start.takeUnless { isWholeDay },
                preferredEnd = end.takeUnless { isWholeDay },
                selection = null
            )
        }
        refreshBlocks()
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun onLinkChange(link: String) {
        _uiState.update { it.copy(link = link) }
    }

    // Looks for the free blocks again with the current filters. Only the latest call survives.
    private fun refreshBlocks() {
        loadJob?.cancel() // Cancel any current coroutine
        val state = _uiState.value

        if (state.hoursError != null) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        loadJob = viewModelScope.launch {
            // Debounce rapidly emitted UI updates (from slider drags)
            delay(REFRESH_DEBOUNCE_MS)
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }
            try {
                val zone = ZoneId.systemDefault()
                val result = groupRepository.freeBlocks(
                    groupId = groupId,
                    range = state.rangeStart to state.rangeEnd,
                    duration = state.duration,
                    zone = zone,
                    preferredHours = state.preferredHours,
                    blocked = groupEventSlots()
                )
                busyByUser = result.busyByUser
                _uiState.update {
                    it.copy(
                        days = result.options.toDays(result.syncedCount, result.names, zone),
                        unsyncedCount = result.unsyncedCount,
                        isLoading = false,
                        selection = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(days = emptyList(), isLoading = false, loadFailed = true) }
            }
        }
    }

    // Time taken by the group's upcoming events. The event being edited doesn't block itself,
    private suspend fun groupEventSlots(): List<TimeSlot> {
        val now = Instant.now()
        return eventRepository.getEventsOnce(groupId)
            .filter { !it.isCancelled && it.id != eventId && it.endAt > now }
            .map { TimeSlot(it.startAt.toEpochMilli(), it.endAt.toEpochMilli()) }
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

    // Saves the selected block as an event; [onSuccess] runs once it is stored.
    // When editing, no selected block means only the title and link change.
    fun onConfirm(onSuccess: () -> Unit = {}) {
        _uiState.update { it.copy(showErrors = true) }
        val state = _uiState.value
        if (!state.isTitleValid || !state.isLinkValid) return
        val selection = state.selection
        if (selection == null && !state.isEditing) return
        if (!state.canConfirm || state.isSaving) return

        _uiState.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                val start = selection?.let {
                    it.date.atTime(it.startTime).atZone(ZoneId.systemDefault()).toInstant()
                }
                val end = start?.plus(state.duration)
                // Who is free during the whole event starts as confirmed
                val confirmedIds = if (start != null && end != null) {
                    freeMembers(start.toEpochMilli(), end.toEpochMilli())
                } else emptyList()

                if (eventId == null) {
                    eventRepository.createEvent(
                        groupId = groupId,
                        title = state.title,
                        link = state.link,
                        startAt = checkNotNull(start).toTimestamp(),
                        endAt = checkNotNull(end).toTimestamp(),
                        confirmedIds = confirmedIds
                    )
                } else {
                    eventRepository.updateEvent(
                        groupId = groupId,
                        eventId = eventId,
                        title = state.title,
                        link = state.link,
                        startAt = start?.toTimestamp(),
                        endAt = end?.toTimestamp(),
                        confirmedIds = confirmedIds
                    )
                }
                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    // The selected block can be longer than the event and its start can be moved, so the free
    // members are checked again against the final time instead of taken from the block.
    private fun freeMembers(start: Long, end: Long): List<String> =
        busyByUser.filterValues { isFree(it, start, end) }.keys.sorted()

    private fun Instant.toTimestamp() = Timestamp(epochSecond, nano)

    private companion object {
        const val REFRESH_DEBOUNCE_MS = 300L
    }
}

// Groups the options by day. Each day shows its best attendee count, and its blocks are the
// earliest MAX_BLOCKS_PER_DAY slots with that count (each one as long as the event). Best days first.
internal fun List<MeetingOption>.toDays(
    totalCount: Int,
    names: Map<String, String>,
    zone: ZoneId
): List<DayAvailability> =
    // Group by dates
    groupBy { Instant.ofEpochMilli(it.slot.start).atZone(zone).toLocalDate() }
        .map { (date, options) ->
            val best = options.maxOf { it.attendees.size }
            val blocks = options
                .filter { it.attendees.size == best } // Show the best options
                .sortedBy { it.slot.start }
                .take(MAX_BLOCKS_PER_DAY)
                .map { option ->
                    FreeBlock(
                        start = option.slot.start.toLocalTime(zone),
                        end = option.slot.end.toLocalTime(zone),
                        attendees = option.attendees.map { names[it] ?: "Unknown member" },
                        missing = option.missing.map { names[it] ?: "Unknown member" }
                    )
                }
            DayAvailability(
                date = date,
                freeCount = best,
                totalCount = totalCount,
                blocks = blocks,
                busyNote = if (best < totalCount) {
                    "${totalCount - best} of $totalCount members are busy during these blocks."
                } else null
            )
        }
        .sortedWith(compareByDescending<DayAvailability> { it.freeCount }.thenBy { it.date })

private const val MAX_BLOCKS_PER_DAY = 5

private fun Long.toLocalTime(zone: ZoneId): LocalTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalTime()
