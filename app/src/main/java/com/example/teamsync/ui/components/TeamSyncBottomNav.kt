package com.example.teamsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon

enum class BottomNavTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    GROUPS("Groups", Icons.Outlined.Group, Icons.Filled.Group),
    CREATE("Create", Icons.Outlined.Add, Icons.Filled.Add),
    ACCOUNT("Account", Icons.Outlined.Person, Icons.Filled.Person)
}

@Composable
fun TeamSyncBottomNav(
    selectedTab: BottomNavTab,
    onTabClick: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    accountPhotoUrl: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            BottomNavTab.entries.forEach { tab ->
                BottomNavItem(
                    tab = tab,
                    selected = tab == selectedTab,
                    onClick = { onTabClick(tab) },
                    photoUrl = if (tab == BottomNavTab.ACCOUNT) accountPhotoUrl else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: BottomNavTab,
    selected: Boolean,
    onClick: () -> Unit,
    photoUrl: String?,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val iconColor = if (selected) colors.primary else colors.outline
    Column(
        modifier = modifier.clickable(role = Role.Tab, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = tab.label,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(iconColor),
                error = ColorPainter(iconColor),
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .then(if (selected) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier)
            )
        } else {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.label,
                tint = iconColor,
                modifier = Modifier
                    .size(22.dp)
            )
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) colors.primary else colors.onSurfaceVariant
        )
    }
}
