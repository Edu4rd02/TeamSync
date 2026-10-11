package com.example.teamsync.data.model

import java.time.Instant

enum class EventStatus(val value: String) {
    SCHEDULED("Scheduled"),
    CANCELLED("Cancelled");

    companion object {
        fun fromValue(value: String?): EventStatus =
            entries.firstOrNull { it.value == value } ?: SCHEDULED
    }
}

data class Event(
    val id: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: EventStatus = EventStatus.SCHEDULED
)
