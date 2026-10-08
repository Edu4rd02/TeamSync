package com.example.teamsync.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.teamsync.data.model.Group
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.components.TeamSyncBottomNav
import com.example.teamsync.ui.components.TeamSyncHeader
import com.example.teamsync.ui.MyGroupsUiState
import com.example.teamsync.ui.MyGroupsViewModel
import com.example.teamsync.ui.theme.TeamSyncTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR
)

private fun hasCalendarPermission(context: Context) = CALENDAR_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun MyGroupsScreen(
    onGroupClick: (Group) -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    viewModel: MyGroupsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MyGroupsContent(
        uiState = uiState,
        onGroupClick = onGroupClick,
        onJoinGroup = viewModel::joinGroup,
        onJoinErrorDismissed = viewModel::onJoinErrorDismissed,
        onJoinSuccess = viewModel::onJoinSuccess,
        onTabClick = onTabClick
    )
}

@Composable
fun MyGroupsContent(
    uiState: MyGroupsUiState,
    onGroupClick: (Group) -> Unit,
    onJoinGroup: (String, Boolean) -> Unit,
    onJoinErrorDismissed: () -> Unit,
    onJoinSuccess: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Joins the group whether or not the calendar permission was granted
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = CALENDAR_PERMISSIONS.all { results[it] == true }
        onJoinGroup(joinCode, granted)
    }

    LaunchedEffect(uiState.joinSuccess) {
        if (uiState.joinSuccess) {
            showJoinGroupDialog = false
            joinCode = ""
            onJoinSuccess()
        }
    }

    if (showJoinGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinGroupDialog = false
                onJoinErrorDismissed()
            },
            title = { Text(text = "Join Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the group invitation code:")
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = {
                            joinCode = it
                            if (uiState.joinError != null) onJoinErrorDismissed()
                        },
                        label = { Text("Invitation Code") },
                        singleLine = true,
                        isError = uiState.joinError != null,
                        supportingText = uiState.joinError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = joinCode.isNotBlank() && !uiState.isJoining,
                    onClick = {
                        if (hasCalendarPermission(context)) onJoinGroup(joinCode, true)
                        else permissionLauncher.launch(CALENDAR_PERMISSIONS)
                    }
                ) {
                    if (uiState.isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Join")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinGroupDialog = false
                    onJoinErrorDismissed()
                }) {
                    Text(
                        text = "Cancel",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TeamSyncHeader(title = "My Groups")

        if (uiState.isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    JoinWithCodeButton(onClick = { showJoinGroupDialog = true })
                }
                items(uiState.groups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        isAdmin = group.isOwnedBy(uiState.currentUserId),
                        onClick = { onGroupClick(group) }
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
private fun JoinWithCodeButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Join with code",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    isAdmin: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                RoleBadge(isAdmin = isAdmin)
            }
            Text(
                text = "${group.memberIds.size} members",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoleBadge(isAdmin: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (isAdmin) "Admin" else "Member",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun MyGroupsContentPreview() {
    TeamSyncTheme {
        MyGroupsContent(
            uiState = MyGroupsUiState(
                isLoading = false,
                groups = listOf("Capstone Team", "Design Club", "Study Group").mapIndexed { i, name ->
                    Group(
                        id = "$i",
                        name = name,
                        memberIds = listOf("me", "a", "b", "c", "d"),
                        ownerId = if (i == 0) "me" else "a",
                        invitationCode = "ABC123",
                        workDays = DayOfWeek.entries.take(5),
                        workStart = LocalTime.of(7, 0),
                        workEnd = LocalTime.of(17, 0),
                        createdAt = Instant.EPOCH,
                        updatedAt = Instant.EPOCH
                    )
                },
                currentUserId = "me"
            ),
            onGroupClick = {},
            onJoinGroup = { _, _ -> },
            onJoinErrorDismissed = {},
            onJoinSuccess = {},
            onTabClick = {}
        )
    }
}
