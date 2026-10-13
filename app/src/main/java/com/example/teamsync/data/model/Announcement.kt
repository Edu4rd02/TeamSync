package com.example.teamsync.data.model

import java.time.Instant

data class Announcement(
    val id: String,
    val title: String,
    val text: String,
    val createdAt: Instant
)
