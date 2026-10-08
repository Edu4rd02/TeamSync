package com.example.teamsync.data.model

import java.time.Instant

data class TimeSlot(
    val start: Long,
    val end: Long
)

data class BusyBlocks (
    val id: String,
    val blocks: List<TimeSlot> = emptyList(),
    val updatedAt: Instant
)