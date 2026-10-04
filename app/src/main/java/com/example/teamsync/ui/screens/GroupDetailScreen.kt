package com.example.teamsync.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.teamsync.ui.FeedFilter
import com.example.teamsync.ui.FeedItem
import com.example.teamsync.ui.FeedItemType
import com.example.teamsync.ui.GroupDetailUiState
import com.example.teamsync.ui.GroupDetailViewModel
import com.example.teamsync.ui.NextEvent
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.components.TeamSyncBottomNav
import com.example.teamsync.ui.components.TeamSyncHeader
import com.example.teamsync.ui.theme.TeamSyncTheme

private val AnnouncementBackground = Color(0xFFEDE9FE)
private val AnnouncementContent = Color(0xFF8B5CF6)

@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    onProposeEvent: () -> Unit = {},
    onNewAnnouncement: () -> Unit = {},
    viewModel: GroupDetailViewModel = viewModel(
        factory = viewModelFactory {
            // Initializer to pass arguments to the viewModel
            initializer { GroupDetailViewModel(createSavedStateHandle()) }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    GroupDetailContent(
        uiState = uiState,
        onBack = onBack,
        onFilterSelected = viewModel::onFilterSelected,
        onProposeEvent = onProposeEvent,
        onNewAnnouncement = onNewAnnouncement,
        onTabClick = onTabClick
    )
}

@Composable
fun GroupDetailContent(
    uiState: GroupDetailUiState,
    onBack: () -> Unit,
    onFilterSelected: (FeedFilter) -> Unit,
    onProposeEvent: () -> Unit,
    onNewAnnouncement: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TeamSyncHeader(
            title = uiState.group?.name.orEmpty(),
            onBack = onBack
        )

        when {
            uiState.isLoading -> CenteredBox { CircularProgressIndicator() }
            uiState.group == null -> CenteredBox {
                Text(
                    text = "This group does not exist anymore",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    FilterRow(selected = uiState.filter, onSelected = onFilterSelected)

                    if (uiState.showNextEvent) {
                        SectionLabel("NEXT EVENT")
                        NextEventCard(uiState.nextEvent!!)
                    }

                    SectionLabel("EARLIER IN THIS GROUP")
                    if (uiState.visibleFeed.isEmpty()) {
                        Text(
                            text = "Nothing here yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        uiState.visibleFeed.forEach { item ->
                            key(item.id) { FeedItemCard(item) }
                        }
                    }
                }

                // Only the admin can propose events and post announcements.
                if (uiState.isAdmin) {
                    ActionBar(
                        onProposeEvent = onProposeEvent,
                        onNewAnnouncement = onNewAnnouncement
                    )
                }
            }
        }

        TeamSyncBottomNav(
            selectedTab = BottomNavTab.GROUPS,
            onTabClick = onTabClick,
            accountPhotoUrl = uiState.photoUrl
        )
    }
}

@Composable
private fun ColumnScope.CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun FilterRow(selected: FeedFilter, onSelected: (FeedFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip("All", selected == FeedFilter.ALL) { onSelected(FeedFilter.ALL) }
        FilterChip("Events", selected == FeedFilter.EVENTS) { onSelected(FeedFilter.EVENTS) }
        FilterChip("Announcements", selected == FeedFilter.ANNOUNCEMENTS) {
            onSelected(FeedFilter.ANNOUNCEMENTS)
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.background,
                shape = shape
            )
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NextEventCard(event: NextEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = event.title,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = event.dateLabel,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                        RoundedCornerShape(9.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${event.confirmedCount} of ${event.totalCount} confirmed",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            if (event.inCalendar) {
                Text(
                    text = "In your calendar",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun FeedItemCard(item: FeedItem) {
    val isAnnouncement = item.type == FeedItemType.ANNOUNCEMENT
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isAnnouncement) AnnouncementBackground
                            else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isAnnouncement) "ANNOUNCEMENT" else "EVENT",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAnnouncement) AnnouncementContent
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = item.timeLabel,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = item.title,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            item.author?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActionBar(onProposeEvent: () -> Unit, onNewAnnouncement: () -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val shape = RoundedCornerShape(13.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.primary, shape)
                    .clickable(onClick = onProposeEvent)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Propose event",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                    .clickable(onClick = onNewAnnouncement)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "New announcement",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun GroupDetailContentPreview() {
    TeamSyncTheme {
        GroupDetailContent(
            uiState = GroupDetailUiState(
                isLoading = false,
                group = previewGroup,
                isAdmin = true,
                nextEvent = NextEvent(
                    title = "Sprint review",
                    dateLabel = "Thursday, Sep 17 · 4:00 – 5:00 PM",
                    confirmedCount = 4,
                    totalCount = 5,
                    inCalendar = true
                ),
                feed = listOf(
                    FeedItem("1", FeedItemType.ANNOUNCEMENT, "Bring the API docs printed for tomorrow", "Today 09:40", "Mariana Robles"),
                    FeedItem("2", FeedItemType.ANNOUNCEMENT, "Demo moved to next week", "Yesterday 18:20", "Mariana Robles"),
                    FeedItem("3", FeedItemType.EVENT, "Design sync moved to 11:00 AM", "Sep 14")
                )
            ),
            onBack = {},
            onFilterSelected = {},
            onProposeEvent = {},
            onNewAnnouncement = {},
            onTabClick = {}
        )
    }
}

private val previewGroup = com.example.teamsync.data.model.Group(
    id = "1",
    name = "Capstone Team",
    memberIds = listOf("me", "a", "b", "c", "d"),
    ownerId = "me",
    invitationCode = "ABC123",
    workDays = java.time.DayOfWeek.entries.take(5),
    workStart = java.time.LocalTime.of(7, 0),
    workEnd = java.time.LocalTime.of(17, 0),
    createdAt = java.time.Instant.EPOCH,
    updatedAt = java.time.Instant.EPOCH
)
