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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.teamsync.data.CalendarWriter
import com.example.teamsync.data.EventCalendarSync
import com.example.teamsync.data.model.ConfirmationStatus
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.EventStatus
import com.example.teamsync.data.repository.EventRepository
import com.example.teamsync.ui.AttendeeRow
import com.example.teamsync.ui.EventDetailUiState
import com.example.teamsync.ui.EventDetailViewModel
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.components.MemberAvatar
import com.example.teamsync.ui.components.TeamSyncBottomNav
import com.example.teamsync.ui.components.TeamSyncHeader
import com.example.teamsync.ui.theme.TeamSyncTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ConfirmedBackground = Color(0xFFDCF4E6)
private val ConfirmedContent = Color(0xFF16804A)
private val WaitingBackground = Color(0xFFFEF3C7)
private val WaitingContent = Color(0xFFB45309)
private val RejectedBackground = Color(0xFFFEE2E2)
private val RejectedContent = Color(0xFFB91C1C)
private val CalendarDot = Color(0xFF4ADE80)


private val DayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val CompactTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.US)

@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    viewModel: EventDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                EventDetailViewModel(
                    savedStateHandle = createSavedStateHandle(),
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

    EventDetailContent(
        uiState = uiState,
        onBack = onBack,
        onEdit = onEdit,
        onConfirm = viewModel::confirm,
        onReject = viewModel::reject,
        onDismissConflict = viewModel::dismissConflict,
        onDismissRevoked = viewModel::dismissRevoked,
        onCancelEvent = viewModel::cancelEvent,
        onTabClick = onTabClick
    )
}

@Composable
fun EventDetailContent(
    uiState: EventDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: (force: Boolean) -> Unit,
    onReject: () -> Unit,
    onDismissConflict: () -> Unit,
    onDismissRevoked: () -> Unit,
    onCancelEvent: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }

    if (uiState.showConflictDialog) {
        AlertDialog(
            onDismissRequest = onDismissConflict,
            title = { Text("You're busy at that time") },
            text = { Text("Your calendar has something else during this event. Confirm anyway?") },
            confirmButton = { TextButton(onClick = { onConfirm(true) }) { Text("Confirm anyway") } },
            dismissButton = { TextButton(onClick = onDismissConflict) { Text("Not now") } }
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel event?") },
            text = { Text("Everyone will see it as cancelled and it will be removed from their calendars.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    onCancelEvent()
                }) { Text("Cancel event", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("Keep it") } }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TeamSyncHeader(
            title = "Event",
            onBack = onBack,
            actions = {
                // Only the admin can edit or cancel, and only while the event is still ahead
                if (uiState.canEdit) {
                    HeaderAction(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit event", modifier = Modifier.size(18.dp))
                    }
                    HeaderAction(onClick = { showCancelDialog = true }, enabled = !uiState.isCancelling) {
                        Icon(
                            Icons.Outlined.EventBusy,
                            contentDescription = "Cancel event",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )

        val event = uiState.event
        when {
            uiState.isLoading -> CenteredMessage { CircularProgressIndicator() }
            event == null -> CenteredMessage {
                Text(
                    text = "This event does not exist anymore",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                EventCard(event = event, groupName = uiState.groupName, inCalendar = uiState.inCalendar)

                if (event.isCancelled) {
                    Notice(
                        text = "This event was cancelled.",
                        background = RejectedBackground,
                        content = RejectedContent
                    )
                }
                if (uiState.wasRevoked) {
                    Notice(
                        text = "You were confirmed automatically, but your calendar has something else " +
                                "at that time. You're back to waiting.",
                        background = WaitingBackground,
                        content = WaitingContent,
                        onClick = onDismissRevoked
                    )
                }
                if (uiState.actionFailed) {
                    Notice(
                        text = "Something went wrong. Try again.",
                        background = RejectedBackground,
                        content = RejectedContent
                    )
                }

                if (uiState.canRespond) {
                    ResponseCard(
                        status = uiState.myStatus,
                        isResponding = uiState.isResponding,
                        onConfirm = { onConfirm(false) },
                        onReject = onReject
                    )
                }

                ConfirmationsCard(
                    attendees = uiState.attendees,
                    confirmedCount = uiState.confirmedCount,
                    totalCount = uiState.totalCount
                )
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
private fun ColumnScope.CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun HeaderAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

@Composable
private fun EventCard(event: Event, groupName: String, inCalendar: Boolean) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = groupName.uppercase(),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = onPrimary.copy(alpha = 0.75f)
        )
        Text(
            text = event.title,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            color = onPrimary,
            textDecoration = if (event.isCancelled) TextDecoration.LineThrough else null
        )
        Text(
            text = formatEventTime(event),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = onPrimary.copy(alpha = 0.85f)
        )

        event.link?.let { link ->
            val uriHandler = LocalUriHandler.current
            Text(
                text = link,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = onPrimary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                modifier = Modifier.clickable {
                    // Links can be saved without a scheme (e.g. "meet.google.com/abc")
                    val uri = if ("://" in link) link else "https://$link"
                    runCatching { uriHandler.openUri(uri) }
                }
            )
        }

        if (inCalendar) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .background(onPrimary.copy(alpha = 0.18f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CalendarDot, CircleShape)
                )
                Text(
                    text = "Added to your device calendar",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = onPrimary
                )
            }
        }
    }
}

@Composable
private fun Notice(
    text: String,
    background: Color,
    content: Color,
    onClick: (() -> Unit)? = null
) {
    Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = content,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    )
}

// Waiting: two buttons. Once answered: one button with the answer that switches it.
@Composable
private fun ResponseCard(
    status: ConfirmationStatus?,
    isResponding: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    CardSurface {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Your response",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (status) {
                null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ResponseButton(
                        text = "Confirm",
                        background = ConfirmedContent,
                        enabled = !isResponding,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    )
                    ResponseButton(
                        text = "Can't make it",
                        background = MaterialTheme.colorScheme.surface,
                        content = RejectedContent,
                        border = RejectedContent,
                        enabled = !isResponding,
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    )
                }
                ConfirmationStatus.CONFIRMED -> ResponseButton(
                    text = "You confirmed · tap to change",
                    background = ConfirmedContent,
                    enabled = !isResponding,
                    onClick = onReject,
                    showDot = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ConfirmationStatus.REJECTED -> ResponseButton(
                    text = "You can't make it · tap to change",
                    background = RejectedContent,
                    enabled = !isResponding,
                    onClick = onConfirm,
                    showDot = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResponseButton(
    text: String,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: Color = Color.White,
    border: Color? = null,
    showDot: Boolean = false
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background.copy(alpha = if (enabled) 1f else 0.6f))
            .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(content, CircleShape)
            )
        }
        Text(
            text = text,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}

@Composable
private fun ConfirmationsCard(attendees: List<AttendeeRow>, confirmedCount: Int, totalCount: Int) {
    CardSurface {
        Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 5.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirmations",
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Chip(
                    text = "$confirmedCount of $totalCount",
                    background = ConfirmedBackground,
                    content = ConfirmedContent
                )
            }
            attendees.forEachIndexed { index, attendee ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AttendeeItem(attendee)
            }
        }
    }
}

@Composable
private fun AttendeeItem(attendee: AttendeeRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MemberAvatar(attendee.uid, attendee.name, attendee.photoUrl)
        Text(
            text = attendee.name,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        when (attendee.status) {
            ConfirmationStatus.CONFIRMED -> Chip("Confirmed", ConfirmedBackground, ConfirmedContent)
            ConfirmationStatus.REJECTED -> Chip("Can't make it", RejectedBackground, RejectedContent)
            null -> Chip("Waiting", WaitingBackground, WaitingContent)
        }
    }
}

@Composable
private fun Chip(text: String, background: Color, content: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = content,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

// "Thursday, Sep 17 · 4:00 – 5:00 PM", or both periods when they differ ("11:30 AM – 12:30 PM")
private fun formatEventTime(event: Event): String {
    val zone = ZoneId.systemDefault()
    val start = event.startAt.atZone(zone)
    val end = event.endAt.atZone(zone)
    val samePeriod = (start.hour < 12) == (end.hour < 12)
    val startText = start.format(if (samePeriod) CompactTimeFormatter else TimeFormatter)
    val endText = end.format(TimeFormatter)
    val endDay = if (end.toLocalDate() != start.toLocalDate()) "${end.format(DayFormatter)} " else ""
    return "${start.format(DayFormatter)} · $startText – $endDay$endText"
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun EventDetailContentPreview() {
    val start = Instant.now().plusSeconds(86_400)
    TeamSyncTheme {
        EventDetailContent(
            uiState = EventDetailUiState(
                isLoading = false,
                event = Event(
                    id = "e1",
                    title = "Sprint review",
                    startAt = start,
                    endAt = start.plusSeconds(3_600),
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH,
                    link = "https://meet.google.com/abc-defg-hij",
                    status = EventStatus.SCHEDULED
                ),
                groupName = "Capstone Team",
                isAdmin = true,
                myStatus = ConfirmationStatus.CONFIRMED,
                inCalendar = true,
                attendees = listOf(
                    AttendeeRow("1", "Anthony James", status = ConfirmationStatus.CONFIRMED),
                    AttendeeRow("2", "Mariana Robles", status = ConfirmationStatus.CONFIRMED),
                    AttendeeRow("3", "Diego Fuentes"),
                    AttendeeRow("4", "Sofía Lara", status = ConfirmationStatus.REJECTED),
                    AttendeeRow("5", "Iván Cortés", status = ConfirmationStatus.CONFIRMED)
                )
            ),
            onBack = {},
            onEdit = {},
            onConfirm = {},
            onReject = {},
            onDismissConflict = {},
            onDismissRevoked = {},
            onCancelEvent = {},
            onTabClick = {}
        )
    }
}
