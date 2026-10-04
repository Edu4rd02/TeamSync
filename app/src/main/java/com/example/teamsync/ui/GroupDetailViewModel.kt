package com.example.teamsync.ui

import androidx.lifecycle.SavedStateHandle
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
    val author: String? = null
)

data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val group: Group? = null,
    val isAdmin: Boolean = false,
    val photoUrl: String? = null,
    val filter: FeedFilter = FeedFilter.ALL,
    val nextEvent: NextEvent? = null,
    val feed: List<FeedItem> = emptyList()
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
    private val groupRepository: GroupRepository = GroupRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _uiState = MutableStateFlow(GroupDetailUiState(photoUrl = authRepository.photoUrl))
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            groupRepository.getGroup(groupId)
                .catch { _uiState.update { state -> state.copy(isLoading = false) } }
                .collect { group ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            group = group,
                            isAdmin = group?.isOwnedBy(authRepository.userId) == true
                        )
                    }
                }
        }
    }

    fun onFilterSelected(filter: FeedFilter) = _uiState.update { it.copy(filter = filter) }
}
