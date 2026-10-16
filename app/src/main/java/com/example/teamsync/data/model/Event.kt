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
    val link: String? = null,
    val status: EventStatus = EventStatus.SCHEDULED
) {
    val isCancelled: Boolean get() = status == EventStatus.CANCELLED
}

// A member without a confirmation document is "waiting".
enum class ConfirmationStatus(val value: String) {
    CONFIRMED("confirmed"),
    REJECTED("rejected");

    companion object {
        fun fromValue(value: String?): ConfirmationStatus? = entries.firstOrNull { it.value == value }
    }
}

data class Confirmation(
    val uid: String,
    val status: ConfirmationStatus,
    // Null when the admin confirmed the member automatically (they were free) and they haven't answered
    val respondedAt: Instant? = null
) {
    val isAutomatic: Boolean get() = respondedAt == null
}
