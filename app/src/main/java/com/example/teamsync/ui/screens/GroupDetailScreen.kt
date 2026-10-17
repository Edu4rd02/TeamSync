package com.example.teamsync.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.example.teamsync.data.CalendarDataSource
import com.example.teamsync.data.CalendarSync
import com.example.teamsync.data.CalendarWriter
import com.example.teamsync.data.EventCalendarSync
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.model.Member
import com.example.teamsync.data.model.MemberRole
import com.example.teamsync.data.repository.EventRepository
import com.example.teamsync.data.repository.GroupRepository
import com.example.teamsync.ui.FeedFilter
import com.example.teamsync.ui.FeedItem
import com.example.teamsync.ui.FeedItemType
import com.example.teamsync.ui.GroupDetailUiState
import com.example.teamsync.ui.GroupDetailViewModel
import com.example.teamsync.ui.NextEvent
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.components.CenteredBox
import com.example.teamsync.ui.components.MemberAvatar
import com.example.teamsync.ui.components.TeamSyncBottomNav
import com.example.teamsync.ui.components.TeamSyncHeader
import com.example.teamsync.ui.theme.TeamSyncTheme

private val AnnouncementBackground = Color(0xFFEDE9FE)
private val AnnouncementContent = Color(0xFF8B5CF6)

@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    onProposeEvent: (Group) -> Unit,
    onEventClick: (eventId: String) -> Unit,
    viewModel: GroupDetailViewModel = viewModel(
        factory = viewModelFactory {
            // Initializer to pass arguments to the viewModel
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                GroupDetailViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    calendarSync = CalendarSync(
                        GroupRepository(),
                        CalendarDataSource(application)
                    ),
                    eventCalendarSync = EventCalendarSync(
                        EventRepository(),
                        CalendarWriter(application)
                    )
                )
            }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()


    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit){
        if (Build.VERSION.SDK_INT >= 33){
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The user left or deleted the group: there is nothing left to show here
    LaunchedEffect(uiState.exited) {
        if (uiState.exited) onBack()
    }

    GroupDetailContent(
        uiState = uiState,
        onBack = onBack,
        onFilterSelected = viewModel::onFilterSelected,
        onProposeEvent = onProposeEvent,
        onEventClick = onEventClick,
        onPublishAnnouncement = viewModel::publishAnnouncement,
        onLeaveGroup = viewModel::leaveGroup,
        onDeleteGroup = viewModel::deleteGroup,
        onRemoveMember = viewModel::removeMember,
        onTabClick = onTabClick
    )
}

enum class GroupDetailTab(val label: String) { FEED("Feed"), INFO("Info") }

@Composable
fun GroupDetailContent(
    uiState: GroupDetailUiState,
    onBack: () -> Unit,
    onFilterSelected: (FeedFilter) -> Unit,
    onProposeEvent: (Group) -> Unit,
    onPublishAnnouncement: (title: String, text: String, onSuccess: () -> Unit) -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    onEventClick: (eventId: String) -> Unit = {},
    onLeaveGroup: () -> Unit = {},
    onDeleteGroup: () -> Unit = {},
    onRemoveMember: (memberId: String) -> Unit = {},
    initialTab: GroupDetailTab = GroupDetailTab.FEED
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var showAnnouncementDialog by rememberSaveable { mutableStateOf(false) }
    // Leave (member) or delete (admin): each role only has one of them, so one flag is enough
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    // uid of the member the admin is about to remove
    var memberToRemove by rememberSaveable { mutableStateOf<String?>(null) }

    if (showAnnouncementDialog) {
        NewAnnouncementDialog(
            isPublishing = uiState.isPublishing,
            onDismiss = { showAnnouncementDialog = false },
            onPublish = { title, text ->
                onPublishAnnouncement(title, text) { showAnnouncementDialog = false }
            }
        )
    }

    // A user removed while the dialog was open has nothing left to leave
    if (confirmExit && uiState.isMember) {
        val groupName = uiState.group?.name.orEmpty()
        ConfirmDestructiveDialog(
            title = if (uiState.isAdmin) "Delete group?" else "Leave group?",
            text = if (uiState.isAdmin) {
                "Everyone will lose access to $groupName, its events and announcements, and its " +
                    "upcoming events will be removed from their calendars. This can't be undone."
            } else {
                "You'll stop seeing $groupName's events and announcements, and its upcoming " +
                    "events will be removed from your calendar."
            },
            confirmLabel = when {
                !uiState.isLeaving -> if (uiState.isAdmin) "Delete" else "Leave"
                uiState.isAdmin -> "Deleting…"
                else -> "Leaving…"
            },
            isWorking = uiState.isLeaving,
            onConfirm = if (uiState.isAdmin) onDeleteGroup else onLeaveGroup,
            onDismiss = { confirmExit = false }
        )
    }

    memberToRemove?.let { uid ->
        val name = uiState.members.firstOrNull { it.uid == uid }?.displayName ?: "This member"
        ConfirmDestructiveDialog(
            title = "Remove member?",
            text = "$name will lose access to the group, and its upcoming events will be removed " +
                "from their calendar.",
            confirmLabel = "Remove",
            isWorking = false,
            onConfirm = {
                onRemoveMember(uid)
                memberToRemove = null
            },
            onDismiss = { memberToRemove = null }
        )
    }

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
            !uiState.isMember -> CenteredBox {
                Text(
                    text = "You are no longer a member of this group",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                GroupTabs(selected = selectedTab, onSelected = { selectedTab = it })

                when (selectedTab) {
                    GroupDetailTab.FEED -> {
                        FeedTab(uiState = uiState, onFilterSelected = onFilterSelected, onEventClick = onEventClick)

                        // Only the admin can propose events and post announcements.
                        if (uiState.isAdmin) {
                            ActionBar(
                                onProposeEvent = { onProposeEvent(uiState.group) },
                                onNewAnnouncement = { showAnnouncementDialog = true }
                            )
                        }
                    }
                    GroupDetailTab.INFO -> InfoTab(
                        uiState = uiState,
                        onRemoveMember = { memberToRemove = it },
                        onExit = { confirmExit = true }
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
private fun GroupTabs(selected: GroupDetailTab, onSelected: (GroupDetailTab) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        GroupDetailTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelected(tab) },
                text = {
                    Text(
                        text = tab.label,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Upcoming event, events and announcements
@Composable
private fun ColumnScope.FeedTab(
    uiState: GroupDetailUiState,
    onFilterSelected: (FeedFilter) -> Unit,
    onEventClick: (eventId: String) -> Unit
) {
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
            CenteredBox{ Text("Nothing here yet") }
        } else {
            uiState.visibleFeed.forEach { item ->
                key(item.id) {
                    FeedItemCard(
                        item = item,
                        // Only events have a detail screen
                        onClick = if (item.type == FeedItemType.EVENT) {
                            { onEventClick(item.id) }
                        } else null
                    )
                }
            }
        }
    }
}

// Description, members and the way out of the group
@Composable
private fun ColumnScope.InfoTab(
    uiState: GroupDetailUiState,
    onRemoveMember: (memberId: String) -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        SectionLabel("ABOUT")
        InfoCard {
            val description = uiState.group?.description?.takeIf { it.isNotBlank() }
            Text(
                text = description ?: "No description",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (description != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }

        SectionLabel("JOIN CODE")
        InfoCard {
            val joinCode = uiState.group?.invitationCode
            Text(
                text = joinCode ?: "No code",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (joinCode != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }

        SectionLabel("MEMBERS · ${uiState.members.size}")
        if (uiState.members.isEmpty()) {
            EmptyText("No members to show")
        } else {
            InfoCard {
                Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                    uiState.members.forEachIndexed { index, member ->
                        key(member.uid) {
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val isYou = member.uid == uiState.currentUserId
                            MemberItem(
                                member = member,
                                isYou = isYou,
                                // Only the admin removes members, and never themselves
                                onRemove = if (uiState.isAdmin && !isYou) {
                                    { onRemoveMember(member.uid) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        ExitButton(
            label = if (uiState.isAdmin) "Delete group" else "Leave group",
            onClick = onExit
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun MemberItem(member: Member, isYou: Boolean, onRemove: (() -> Unit)?) {
    val name = member.displayName ?: "Unknown member"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(uid = member.uid, name = name, photoUrl = member.photoUrl)
        Text(
            text = if (isYou) "$name (You)" else name,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (member.isAdmin) {
            Text(
                text = "ADMIN",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        onRemove?.let {
            TextButton(
                onClick = it,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = "Remove", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Leave (member) or delete (admin); the confirmation dialog comes first
@Composable
private fun ExitButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(shape)
            .border(1.5.dp, MaterialTheme.colorScheme.error, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
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
private fun FeedItemCard(item: FeedItem, onClick: (() -> Unit)? = null) {
    val isAnnouncement = item.type == FeedItemType.ANNOUNCEMENT
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    if (item.isCancelled) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CANCELLED",
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
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
            item.text?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
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
private fun NewAnnouncementDialog(
    isPublishing: Boolean,
    onDismiss: () -> Unit,
    onPublish: (title: String, text: String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isPublishing) onDismiss() },
        title = { Text("New announcement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Message") },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !isPublishing,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPublish(title, text) },
                enabled = !isPublishing && title.isNotBlank() && text.isNotBlank()
            ) { Text(if (isPublishing) "Publishing…" else "Publish") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPublishing) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConfirmDestructiveDialog(
    title: String,
    text: String,
    confirmLabel: String,
    isWorking: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        // Can't be dismissed while the action is running
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isWorking,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) { Text("Cancel") }
        }
    )
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
            onPublishAnnouncement = { _, _, _ -> },
            onTabClick = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun GroupDetailInfoPreview() {
    TeamSyncTheme {
        GroupDetailContent(
            uiState = GroupDetailUiState(
                isLoading = false,
                group = previewGroup.copy(description = "Final project for Software Engineering II"),
                isAdmin = true,
                currentUserId = "me",
                members = listOf(
                    Member("me", MemberRole.ADMIN, "Mariana Robles", joinedAt = java.time.Instant.EPOCH),
                    Member("a", MemberRole.MEMBER, "Anthony James", joinedAt = java.time.Instant.EPOCH),
                    Member("b", MemberRole.MEMBER, "Diego Fuentes", joinedAt = java.time.Instant.EPOCH)
                )
            ),
            onBack = {},
            onFilterSelected = {},
            onProposeEvent = {},
            onPublishAnnouncement = { _, _, _ -> },
            onTabClick = {},
            initialTab = GroupDetailTab.INFO
        )
    }
}

private val previewGroup = Group(
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
