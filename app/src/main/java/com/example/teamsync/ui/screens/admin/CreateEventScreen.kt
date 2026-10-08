package com.example.teamsync.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.teamsync.ui.BlockSelection
import com.example.teamsync.ui.CreateEventUiState
import com.example.teamsync.ui.CreateEventViewModel
import com.example.teamsync.ui.DayAvailability
import com.example.teamsync.ui.FreeBlock
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.components.TeamSyncBottomNav
import com.example.teamsync.ui.components.TeamSyncHeader
import com.example.teamsync.ui.theme.TeamSyncTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FreeBackground = Color(0xFFDCF4E6)
private val FreeContent = Color(0xFF16804A)
private val PartialBackground = Color(0xFFFEF3C7)
private val PartialContent = Color(0xFFB45309)

private val DayFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)
private val ShortDayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val RangeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val CompactTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.US)

@Composable
fun CreateEventScreen(
    onBack: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    viewModel: CreateEventViewModel = viewModel(
        factory = viewModelFactory {
            // Initializer to pass arguments to the viewModel
            initializer { CreateEventViewModel(createSavedStateHandle()) }
        }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CreateEventContent(
        uiState = uiState,
        onBack = onBack,
        onDurationChange = viewModel::onDurationChange,
        onDateRangeChange = viewModel::onDateRangeChange,
        onBlockSelected = viewModel::onBlockSelected,
        onStartTimeChange = viewModel::onStartTimeChange,
        onConfirmClick = {},
        onTabClick = onTabClick
    )
}

@Composable
fun CreateEventContent(
    uiState: CreateEventUiState,
    onBack: () -> Unit,
    onDurationChange: (Duration) -> Unit,
    onDateRangeChange: (LocalDate, LocalDate) -> Unit,
    onBlockSelected: (LocalDate, FreeBlock) -> Unit,
    onStartTimeChange: (LocalTime) -> Unit,
    onConfirmClick: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDurationDialog by rememberSaveable { mutableStateOf(false) }
    var showRangeDialog by rememberSaveable { mutableStateOf(false) }

    if (showDurationDialog) {
        DurationDialog(
            selected = uiState.duration,
            onDismiss = { showDurationDialog = false },
            onSelect = {
                onDurationChange(it)
                showDurationDialog = false
            }
        )
    }

    if (showRangeDialog) {
        DateRangeDialog(
            start = uiState.rangeStart,
            end = uiState.rangeEnd,
            onDismiss = { showRangeDialog = false },
            onConfirm = { start, end ->
                onDateRangeChange(start, end)
                showRangeDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TeamSyncHeader(title = "Propose Event", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoField(
                    label = "DURATION",
                    value = formatDuration(uiState.duration),
                    onClick = { showDurationDialog = true },
                    modifier = Modifier.weight(1f)
                )
                InfoField(
                    label = "DATE RANGE",
                    value = "${uiState.rangeStart.format(RangeFormatter)} – " +
                            uiState.rangeEnd.format(RangeFormatter),
                    onClick = { showRangeDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Free blocks by day",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                uiState.lastSyncedMinutesAgo?.let {
                    Text(
                        text = "Synced $it min ago",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.days.isEmpty()) {
                Text(
                    text = "No free blocks in this range",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                uiState.days.forEach { day ->
                    DayCard(
                        day = day,
                        selection = uiState.selection?.takeIf { it.date == day.date },
                        onBlockSelected = { onBlockSelected(day.date, it) }
                    )
                }
            }
        }

        ConfirmBar(
            selection = uiState.selection,
            enabled = uiState.canConfirm,
            onTimeChange = onStartTimeChange,
            onClick = onConfirmClick
        )

        TeamSyncBottomNav(
            selectedTab = BottomNavTab.GROUPS,
            onTabClick = onTabClick,
            accountPhotoUrl = uiState.photoUrl
        )
    }
}

@Composable
private fun InfoField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun DayCard(
    day: DayAvailability,
    selection: BlockSelection?,
    onBlockSelected: (FreeBlock) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isSelected = selection != null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = if (isSelected) BorderStroke(2.dp, colors.primary)
        else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = day.date.format(DayFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                AvailabilityBadge(day)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                day.blocks.forEach { block ->
                    BlockChip(
                        block = block,
                        selected = selection?.block == block,
                        onClick = { onBlockSelected(block) }
                    )
                }
            }

            val note = when {
                isSelected -> "Tap a block, then pick the start time inside it."
                else -> day.busyNote
            }
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AvailabilityBadge(day: DayAvailability) {
    val everyoneFree = day.isEveryoneFree
    Text(
        text = if (everyoneFree) "All ${day.totalCount} free" else "${day.freeCount} of ${day.totalCount}",
        style = MaterialTheme.typography.labelSmall,
        color = if (everyoneFree) FreeContent else PartialContent,
        modifier = Modifier
            .background(
                color = if (everyoneFree) FreeBackground else PartialBackground,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun BlockChip(block: FreeBlock, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.primary else colors.background,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Text(
            text = formatBlock(block),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.onPrimary else colors.onSurface,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ConfirmBar(
    selection: BlockSelection?,
    enabled: Boolean,
    onTimeChange: (LocalTime) -> Unit,
    onClick: () -> Unit
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selection != null) {
                TextButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Start time: ${selection.startTime.format(TimeFormatter)} · change")
                }
            }
            Surface(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selection?.let {
                        "Confirm ${it.date.format(ShortDayFormatter)}, ${it.startTime.format(TimeFormatter)}"
                    } ?: "Select a free block",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showPicker && selection != null) {
        StartTimeDialog(
            initial = selection.startTime,
            onDismiss = { showPicker = false },
            onConfirm = {
                onTimeChange(it)
                showPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = { TimePicker(state = state) }
    )
}

private val DurationOptions = listOf(30L, 45L, 60L, 90L, 120L, 180L).map(Duration::ofMinutes)

@Composable
private fun DurationDialog(
    selected: Duration,
    onDismiss: () -> Unit,
    onSelect: (Duration) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duration") },
        text = {
            Column {
                DurationOptions.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = formatDuration(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    start: LocalDate,
    end: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start.toPickerMillis(),
        initialSelectedEndDateMillis = end.toPickerMillis()
    )
    val pickedStart = state.selectedStartDateMillis
    val pickedEnd = state.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickedStart != null && pickedEnd != null,
                onClick = {
                    if (pickedStart != null && pickedEnd != null) {
                        onConfirm(pickedStart.toPickerDate(), pickedEnd.toPickerDate())
                    }
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(500.dp))
    }
}

// The Material date pickers work with UTC midnight timestamps.
private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun formatDuration(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = (duration.toMinutes() % 60).toInt()
    return when {
        minutes == 0 -> if (hours == 1L) "1 hour" else "$hours hours"
        hours == 0L -> "$minutes min"
        else -> "${hours}h $minutes min"
    }
}

// "10:00 AM – 1:00 PM", or "10:00 – 11:30 AM" when both ends share the same period.
private fun formatBlock(block: FreeBlock): String {
    val samePeriod = (block.start.hour < 12) == (block.end.hour < 12)
    return if (samePeriod) {
        "${block.start.format(CompactTimeFormatter)} – ${block.end.format(TimeFormatter)}"
    } else {
        "${block.start.format(TimeFormatter)} – ${block.end.format(TimeFormatter)}"
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CreateEventContentPreview() {
    val thursday = LocalDate.of(2026, 9, 17)
    val morning = FreeBlock(LocalTime.of(10, 0), LocalTime.of(13, 0))
    val afternoon = FreeBlock(LocalTime.of(16, 0), LocalTime.of(17, 30))
    TeamSyncTheme {
        CreateEventContent(
            uiState = CreateEventUiState(
                rangeStart = LocalDate.of(2026, 9, 15),
                rangeEnd = LocalDate.of(2026, 9, 22),
                lastSyncedMinutesAgo = 12,
                days = listOf(
                    DayAvailability(thursday, 5, 5, listOf(morning, afternoon)),
                    DayAvailability(
                        date = LocalDate.of(2026, 9, 16),
                        freeCount = 4,
                        totalCount = 5,
                        blocks = listOf(FreeBlock(LocalTime.of(10, 0), LocalTime.of(11, 30))),
                        busyNote = "Diego is busy during this block."
                    ),
                    DayAvailability(
                        date = LocalDate.of(2026, 9, 18),
                        freeCount = 5,
                        totalCount = 5,
                        blocks = listOf(
                            FreeBlock(LocalTime.of(9, 0), LocalTime.of(10, 30)),
                            FreeBlock(LocalTime.of(14, 0), LocalTime.of(15, 0))
                        )
                    )
                ),
                selection = BlockSelection(thursday, afternoon, LocalTime.of(16, 0))
            ),
            onBack = {},
            onDurationChange = {},
            onDateRangeChange = { _, _ -> },
            onBlockSelected = { _, _ -> },
            onStartTimeChange = {},
            onConfirmClick = {},
            onTabClick = {}
        )
    }
}
