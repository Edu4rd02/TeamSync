package com.example.teamsync.data.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

data class Group(
    val id: String,
    val name: String,
    val description: String? = null,
    val memberIds: List<String>,
    val ownerId: String,
    val invitationCode: String,
    val workDays: List<DayOfWeek>,
    val workStart: LocalTime,
    val workEnd: LocalTime,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun isOwnedBy(userId: String?): Boolean = userId != null && ownerId == userId
}
