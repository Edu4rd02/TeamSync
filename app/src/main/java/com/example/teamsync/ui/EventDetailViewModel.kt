package com.example.teamsync.ui

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.EventCalendarSync
import com.example.teamsync.data.model.Confirmation
import com.example.teamsync.data.model.ConfirmationStatus
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.EventRepository
import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class AttendeeRow(
    val uid: String,
    val name: String,
    val photoUrl: String? = null,
    // Null means the member is still waiting
    val status: ConfirmationStatus? = null
)

data class EventDetailUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val groupName: String = "",
    val isAdmin: Boolean = false,
    val myStatus: ConfirmationStatus? = null,
    val attendees: List<AttendeeRow> = emptyList(),
    val inCalendar: Boolean = false,
    val isResponding: Boolean = false,
    // The user tried to confirm but has something else in their calendar at that time
    val showConflictDialog: Boolean = false,
    // The automatic confirmation was dropped because the user's calendar was busy at that time
    val wasRevoked: Boolean = false,
    val isCancelling: Boolean = false,
    val actionFailed: Boolean = false,
    val photoUrl: String? = null
) {
    val confirmedCount: Int get() = attendees.count { it.status == ConfirmationStatus.CONFIRMED }
    val totalCount: Int get() = attendees.size

    // Answers can't change once the event is cancelled or over
    val canRespond: Boolean
        get() = event?.let { !it.isCancelled && it.endAt > Instant.now() } == true

    val canEdit: Boolean get() = isAdmin && canRespond
}

class EventDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val eventCalendarSync: EventCalendarSync,
    private val groupRepository: GroupRepository = GroupRepository(),
    private val eventRepository: EventRepository = EventRepository(),
    authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val eventId: String = checkNotNull(savedStateHandle["eventId"])
    private val userId: String? = authRepository.userId

    private val _uiState = MutableStateFlow(EventDetailUiState(photoUrl = authRepository.photoUrl))
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    // The last event + own confirmation the device calendar was brought in line with
    private var reconciledFor: Pair<Event, Confirmation?>? = null

    init {
        viewModelScope.launch {
            combine(
                groupRepository.getGroup(groupId),
                groupRepository.getMembers(groupId),
                eventRepository.getEvent(groupId, eventId),
                eventRepository.getConfirmations(groupId, eventId)
            ) { group, members, event, confirmations ->
                // Convert the confirmations into maps
                val byUser = confirmations.associateBy { it.uid }
                val attendees = members
                    // If the group is
                    .filter { group != null && it.uid in group.memberIds }
                    .map { member ->
                        AttendeeRow(
                            uid = member.uid,
                            name = member.displayName ?: "Unknown member",
                            photoUrl = member.photoUrl,
                            status = byUser[member.uid]?.status
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                Triple(group, event, attendees) to byUser[userId]
            }
                .catch { e ->
                    Log.e(TAG, "Error listening to event $eventId", e)
                    _uiState.update { it.copy(isLoading = false) }
                }
                .collect { (data, mine) ->
                    val (group, event, attendees) = data

                    if (group == null){
                        _uiState.update{
                            it.copy(
                                isLoading = false,
                                event = null,
                                attendees = emptyList()
                            )
                        }
                        return@collect
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            event = event,
                            groupName = group.name,
                            isAdmin = group.isOwnedBy(userId),
                            myStatus = mine?.status,
                            attendees = attendees
                        )
                    }
                    if (event != null) reconcile(event, mine)
                }
        }
    }

    // Puts the event in (or takes it out of) the device calendar whenever the event or the user's
    // answer changes, e.g. after confirming, rejecting, or the admin editing or cancelling it.
    private fun reconcile(event: Event, confirmation: Confirmation?) {
        val uid = userId ?: return
        val key = event to confirmation
        if (key == reconciledFor) return
        reconciledFor = key

        viewModelScope.launch {
            val result = eventCalendarSync.reconcileEvent(groupId, event, uid, confirmation)
            _uiState.update {
                it.copy(inCalendar = result.inCalendar, wasRevoked = it.wasRevoked || result.revoked)
            }
        }
    }


    fun confirm(force: Boolean = false) {
        val state = _uiState.value
        val event = state.event ?: return
        val uid = userId ?: return
        if (state.isResponding || !state.canRespond || state.myStatus == ConfirmationStatus.CONFIRMED) return

        _uiState.update { it.copy(isResponding = true, showConflictDialog = false, actionFailed = false) }
        viewModelScope.launch {
            try {
                // Force is used to sync without asking conflicts
                if (!force && eventCalendarSync.hasConflict(groupId, event)) {
                    _uiState.update { it.copy(showConflictDialog = true) }
                    return@launch
                }
                // The listener sees the new answer and adds the event to the calendar
                eventRepository.respond(groupId, eventId, uid, ConfirmationStatus.CONFIRMED)
                _uiState.update { it.copy(wasRevoked = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not confirm event $eventId", e)
                _uiState.update { it.copy(actionFailed = true) }
            } finally {
                _uiState.update { it.copy(isResponding = false) }
            }
        }
    }

    // The listener sees the new answer and removes the event from the calendar
    fun reject() {
        val state = _uiState.value
        val uid = userId ?: return
        if (state.isResponding || !state.canRespond || state.myStatus == ConfirmationStatus.REJECTED) return

        _uiState.update { it.copy(isResponding = true, actionFailed = false) }
        viewModelScope.launch {
            try {
                eventRepository.respond(groupId, eventId, uid, ConfirmationStatus.REJECTED)
                _uiState.update { it.copy(wasRevoked = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not reject event $eventId", e)
                _uiState.update { it.copy(actionFailed = true) }
            } finally {
                _uiState.update { it.copy(isResponding = false) }
            }
        }
    }

    fun dismissConflict() = _uiState.update { it.copy(showConflictDialog = false) }

    fun dismissRevoked() = _uiState.update { it.copy(wasRevoked = false) }

    // Only the admin; each member's device removes it from their calendar when it reconciles
    fun cancelEvent() {
        val state = _uiState.value
        if (!state.canEdit || state.isCancelling) return

        _uiState.update { it.copy(isCancelling = true, actionFailed = false) }
        viewModelScope.launch {
            try {
                eventRepository.cancelEvent(groupId, eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not cancel event $eventId", e)
                _uiState.update { it.copy(actionFailed = true) }
            } finally {
                _uiState.update { it.copy(isCancelling = false) }
            }
        }
    }

    private companion object {
        const val TAG = "EventDetailViewModel"
    }
}
