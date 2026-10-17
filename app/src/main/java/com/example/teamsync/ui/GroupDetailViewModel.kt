package com.example.teamsync.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.teamsync.data.CalendarSync
import com.example.teamsync.data.EventCalendarSync
import android.util.Log
import com.example.teamsync.data.model.Announcement
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.model.Member
import com.example.teamsync.data.repository.AnnouncementsRepository
import com.example.teamsync.data.repository.AuthRepository
import com.example.teamsync.data.repository.EventRepository
import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class FeedFilter { ALL, EVENTS, ANNOUNCEMENTS }

enum class FeedItemType { EVENT, ANNOUNCEMENT }


data class NextEvent(
    val title: String,
    val dateLabel: String,
    val confirmedCount: Int,
    val totalCount: Int,
    val inCalendar: Boolean
)

data class FeedItem(
    val id: String,
    val type: FeedItemType,
    val title: String,
    val timeLabel: String,
    val author: String? = null,
    val text: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val isCancelled: Boolean = false
)

data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val events: List<Event> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val isPublishing: Boolean = false,
    val isAdmin: Boolean = false,
    // False once the admin removed the user while they're looking at the group.
    val isMember: Boolean = true,
    val photoUrl: String? = null,
    val filter: FeedFilter = FeedFilter.ALL,
    val nextEvent: NextEvent? = null,
    val feed: List<FeedItem> = emptyList(),
    val currentUserId: String? = null,
    // Admin first, then by name
    val members: List<Member> = emptyList(),
    // Leaving or deleting the group is in progress
    val isLeaving: Boolean = false,
    // One-shot: the screen closes once the user left or deleted the group
    val exited: Boolean = false
) {
    val visibleFeed: List<FeedItem>
        get() = when (filter) {
            FeedFilter.ALL -> feed
            FeedFilter.EVENTS -> feed.filter { it.type == FeedItemType.EVENT }
            FeedFilter.ANNOUNCEMENTS -> feed.filter { it.type == FeedItemType.ANNOUNCEMENT }
        }

    val showNextEvent: Boolean
        get() = nextEvent != null && filter != FeedFilter.ANNOUNCEMENTS
}

class GroupDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val calendarSync: CalendarSync,
    private val eventCalendarSync: EventCalendarSync,
    private val groupRepository: GroupRepository = GroupRepository(),
    private val eventRepository: EventRepository = EventRepository(),
    private val announcementsRepository: AnnouncementsRepository = AnnouncementsRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(
        GroupDetailUiState(photoUrl = authRepository.photoUrl, currentUserId = authRepository.userId)
    )
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            groupRepository.getGroup(groupId)
                .catch { _uiState.update { state -> state.copy(isLoading = false) } }
                .collect { group ->
                    val wasMember = _uiState.value.isMember
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            group = group,
                            isAdmin = group?.isOwnedBy(authRepository.userId) == true,
                            isMember = group == null || it.isLeaving ||
                                authRepository.userId in group.memberIds
                        )
                    }
                    if (wasMember && !_uiState.value.isMember) {
                        eventCalendarSync.removeGroup(groupId)
                    }
                }
        }

        viewModelScope.launch {
            groupRepository.getMembers(groupId)
                // Without the list the rest of the screen keeps working
                .catch { }
                .collect { members ->
                    _uiState.update {
                        it.copy(
                            members = members.sortedWith(
                                compareByDescending<Member> { member -> member.isAdmin }
                                    .thenBy { member -> member.displayName.orEmpty().lowercase() }
                            )
                        )
                    }
                }
        }

        viewModelScope.launch {
            eventRepository.getEvents(groupId)
                .catch { _uiState.update { state -> state.copy(isLoading = false) } }
                .collect { events ->
                    _uiState.update {
                        it.copy(isLoading = false, events = events).withFeed()
                    }
                }
        }

        viewModelScope.launch {
            announcementsRepository.getAnnouncements(groupId)
                .catch { _uiState.update { state -> state.copy(isLoading = false) } }
                .collect { announcements ->
                    _uiState.update {
                        it.copy(isLoading = false, announcements = announcements).withFeed()
                    }
                }
        }

        // Sync the calendar
        authRepository.userId?.let { userId ->
            viewModelScope.launch {
                val calendarChanged = eventCalendarSync.reconcileGroup(groupId, userId)
                calendarSync.sync(groupId, userId, throttle = !calendarChanged)
            }
        }
    }

    // Events and announcements arrive from separate listeners; merge them newest first.
    private fun GroupDetailUiState.withFeed() = copy(
        feed = (events.map { it.toFeedItem() } + announcements.map { it.toFeedItem() })
            .sortedByDescending { it.createdAt }
    )

    private fun Event.toFeedItem() = FeedItem(
        id = id,
        type = FeedItemType.EVENT,
        title = title,
        timeLabel = formatTime(startAt),
        createdAt = createdAt,
        isCancelled = isCancelled
    )

    private fun Announcement.toFeedItem() = FeedItem(
        id = id,
        type = FeedItemType.ANNOUNCEMENT,
        title = title,
        timeLabel = formatTime(createdAt),
        text = text,
        createdAt = createdAt
    )

    private fun formatTime(instant: Instant): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .format(instant.atZone(ZoneId.systemDefault()))

    fun onFilterSelected(filter: FeedFilter) = _uiState.update { it.copy(filter = filter) }

    fun publishAnnouncement(title: String, text: String, onSuccess: () -> Unit) {
        if (_uiState.value.isPublishing || title.isBlank() || text.isBlank()) return
        _uiState.update { it.copy(isPublishing = true) }
        viewModelScope.launch {
            try {
                announcementsRepository.createAnnouncement(groupId, title.trim(), text.trim())
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Could not publish announcement", e)
            } finally {
                _uiState.update { it.copy(isPublishing = false) }
            }
        }
    }

    fun leaveGroup() {
        // The admin deletes the group instead, and a removed user is already out
        if (_uiState.value.isAdmin || !_uiState.value.isMember) return
        exitGroup { userId -> groupRepository.removeMember(groupId, userId) }
    }

    fun deleteGroup() {
        if (!_uiState.value.isAdmin) return
        exitGroup { groupRepository.deleteGroup(groupId) }
    }

    // Leaving and deleting end the same way: the group's events leave this device's calendar
    // right away (without waiting for the push) and the screen closes.
    // Firestore goes first so a failed write never leaves the user half out of the group.
    private fun exitGroup(action: suspend (userId: String) -> Unit) {
        val userId = authRepository.userId ?: return
        if (_uiState.value.isLeaving) return
        _uiState.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            try {
                action(userId)
                eventCalendarSync.removeGroup(groupId)
                _uiState.update { it.copy(exited = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not leave group $groupId", e)
            } finally {
                _uiState.update { it.copy(isLeaving = false) }
            }
        }
    }

    // The removed member's devices clean their calendar through the push; the admin's stays as is.
    fun removeMember(memberId: String) {
        if (!_uiState.value.isAdmin || memberId == authRepository.userId) return
        viewModelScope.launch {
            try {
                groupRepository.removeMember(groupId, memberId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not remove member $memberId", e)
            }
        }
    }

    private companion object {
        const val TAG = "GroupDetailViewModel"
    }
}
