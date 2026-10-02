package com.example.teamsync.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.teamsync.ui.CreateGroupUiState
import com.example.teamsync.ui.CreateGroupViewModel
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.components.TeamSyncBottomNav
import com.example.teamsync.ui.components.TeamSyncHeader
import com.example.teamsync.ui.theme.Gray400
import com.example.teamsync.ui.theme.TeamSyncTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

@Composable
fun CreateGroupScreen(
    onGroupCreated: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    viewModel: CreateGroupViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isCreated) {
        if (uiState.isCreated) {
            Toast.makeText(context, "Group created", Toast.LENGTH_SHORT).show()
            onGroupCreated()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.onErrorShown()
        }
    }

    CreateGroupContent(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onWorkDayToggle = viewModel::onWorkDayToggle,
        onWorkStartChange = viewModel::onWorkStartChange,
        onWorkEndChange = viewModel::onWorkEndChange,
        onCreateClick = viewModel::createGroup,
        onTabClick = onTabClick
    )
}

@Composable
fun CreateGroupContent(
    uiState: CreateGroupUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onWorkDayToggle: (DayOfWeek) -> Unit,
    onWorkStartChange: (LocalTime) -> Unit,
    onWorkEndChange: (LocalTime) -> Unit,
    onCreateClick: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TeamSyncHeader(title = "New Group")

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LabeledTextField(
                label = "GROUP NAME",
                value = uiState.name,
                onValueChange = onNameChange,
                placeholder = "e.g. Capstone Team"
            )
            LabeledTextField(
                label = "DESCRIPTION (OPTIONAL)",
                value = uiState.description,
                onValueChange = onDescriptionChange,
                placeholder = "What is this group for?",
                singleLine = false
            )
            TeamWindowCard(
                days = uiState.workDays,
                start = uiState.workStart,
                end = uiState.workEnd,
                onDayToggle = onWorkDayToggle,
                onStartChange = onWorkStartChange,
                onEndChange = onWorkEndChange
            )
            AdminNote()
        }

        CreateGroupCta(
            enabled = uiState.canCreate,
            isLoading = uiState.isCreating,
            onClick = onCreateClick
        )

        TeamSyncBottomNav(
            selectedTab = BottomNavTab.CREATE,
            onTabClick = onTabClick,
            accountPhotoUrl = uiState.photoUrl
        )
    }
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(
                    if (isFocused) BorderStroke(1.5.dp, colors.primary)
                    else BorderStroke(1.dp, colors.outlineVariant),
                    shape
                )
                .padding(horizontal = 13.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.titleSmall,
                            color = Gray400
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun TeamWindowCard(
    days: Set<DayOfWeek>,
    start: LocalTime,
    end: LocalTime,
    onDayToggle: (DayOfWeek) -> Unit,
    onStartChange: (LocalTime) -> Unit,
    onEndChange: (LocalTime) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Team window",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Availability is only read inside these hours. Nothing outside them is ever looked at.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    DayChip(
                        day = day,
                        selected = day in days,
                        onToggle = { onDayToggle(day) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField(
                    label = "FROM",
                    time = start,
                    onTimeChange = onStartChange,
                    modifier = Modifier.weight(1f)
                )
                TimeField(
                    label = "TO",
                    time = end,
                    onTimeChange = onEndChange,
                    modifier = Modifier.weight(1f)
                )
            }
            if (start >= end) {
                Text(
                    text = "The end time must be after the start time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DayChip(
    day: DayOfWeek,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(9.dp)
    val fullName = day.getDisplayName(TextStyle.FULL, Locale.US)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.outlineVariant, shape))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = fullName }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.getDisplayName(TextStyle.NARROW, Locale.US),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(11.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = time.format(TimeFormatter),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
            )
        }
    }

    if (showPicker) {
        TimeSelectDialog(
            initial = time,
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
private fun TimeSelectDialog(
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

@Composable
private fun AdminNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "You will be the admin",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "An invite code is generated when the group is created. Only the admin can propose events and post announcements.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.alpha(0.85f)
        )
    }
}

@Composable
private fun CreateGroupCta(
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled || isLoading) 1f else 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    text = "Create group",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CreateGroupContentPreview() {
    TeamSyncTheme {
        CreateGroupContent(
            uiState = CreateGroupUiState(name = "Capstone Team"),
            onNameChange = {},
            onDescriptionChange = {},
            onWorkDayToggle = {},
            onWorkStartChange = {},
            onWorkEndChange = {},
            onCreateClick = {},
            onTabClick = {}
        )
    }
}
