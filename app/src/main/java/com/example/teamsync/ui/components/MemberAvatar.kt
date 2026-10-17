package com.example.teamsync.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val AvatarColors = listOf(
    Color(0xFFF08A3C), Color(0xFF3B82F6), Color(0xFFDB4C86), Color(0xFF17A57A), Color(0xFF8B5CF6)
)

// The member's photo, or a colored circle with their initial (the color is stable per uid)
@Composable
fun MemberAvatar(uid: String, name: String, photoUrl: String?, modifier: Modifier = Modifier) {
    val color = AvatarColors[Math.floorMod(uid.hashCode(), AvatarColors.size)]
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        photoUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
