package com.example.teamsync.data.model

import com.google.firebase.Timestamp

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
    val startAt: Timestamp,
    val endAt: Timestamp,
    val createdAt: Timestamp,
    val updatedAt: Timestamp,
    val status: EventStatus = EventStatus.SCHEDULED
)
