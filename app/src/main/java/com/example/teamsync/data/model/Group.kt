package com.example.teamsync.data.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

    // The group's working window on [date] as epoch millis, or null if it's not a work day.
    fun windowFor(date: LocalDate, zone: ZoneId): Pair<Long, Long>? {
        if (date.dayOfWeek !in workDays) return null
        val from = date.atTime(workStart).atZone(zone).toInstant().toEpochMilli()
        val to = date.atTime(workEnd).atZone(zone).toInstant().toEpochMilli()
        return from to to
    }
}
