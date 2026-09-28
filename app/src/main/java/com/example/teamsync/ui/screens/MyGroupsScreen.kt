package com.example.teamsync.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.model.GroupRole
import com.example.teamsync.ui.components.BottomNavTab
import com.example.teamsync.ui.components.TeamSyncBottomNav
import com.example.teamsync.ui.components.TeamSyncHeader
import com.example.teamsync.ui.MyGroupsUiState
import com.example.teamsync.ui.MyGroupsViewModel
import com.example.teamsync.ui.theme.TeamSyncTheme

@Composable
fun MyGroupsScreen(
    onGroupClick: (Group) -> Unit,
    onJoinWithCodeClick: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    viewModel: MyGroupsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MyGroupsContent(
        uiState = uiState,
        onGroupClick = onGroupClick,
        onJoinWithCodeClick = onJoinWithCodeClick,
        onTabClick = onTabClick
    )
}

@Composable
fun MyGroupsContent(
    uiState: MyGroupsUiState,
    onGroupClick: (Group) -> Unit,
    onJoinWithCodeClick: () -> Unit,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    JoinWithCodeButton(onClick = onJoinWithCodeClick)
                }
                items(uiState.groups, key = { it.id }) { group ->
                    GroupCard(group = group, onClick = { onGroupClick(group) })
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
                RoleBadge(role = group.role)
            }
            Text(
                text = buildString {
                    append("${group.memberCount} members")
                    group.nextEvent?.let { append(" · Next: $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoleBadge(role: GroupRole) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = when (role) {
                GroupRole.ADMIN -> "Admin"
                GroupRole.MEMBER -> "Member"
            },
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
                groups = listOf(
                    Group("1", "Capstone Team", GroupRole.MEMBER, 5, "Thu Sep 17, 4:00 PM"),
                    Group("2", "Design Club", GroupRole.MEMBER, 5, "Thu Sep 17, 4:00 PM"),
                    Group("3", "Study Group", GroupRole.MEMBER, 5, "Thu Sep 17, 4:00 PM")
                )
            ),
            onGroupClick = {},
            onJoinWithCodeClick = {},
            onTabClick = {}
        )
    }
}
