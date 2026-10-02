package com.example.teamsync.data.model

import java.time.Instant

enum class MemberRole { ADMIN, MEMBER }

enum class CalendarStatus { NOT_CONNECTED, CONNECTED, PERMISSION_DENIED }

data class Member(
    val uid: String,
    val role: MemberRole,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val joinedAt: Instant,
    val lastSyncAt: Instant? = null,
    val calendarStatus: CalendarStatus = CalendarStatus.NOT_CONNECTED
) {
    val isAdmin: Boolean get() = role == MemberRole.ADMIN
}
