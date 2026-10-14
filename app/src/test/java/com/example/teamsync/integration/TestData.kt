package com.example.teamsync.integration

import com.example.teamsync.data.model.Announcement
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.Group
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

fun testGroup(
    id: String = "g1",
    name: String = "Capstone Team",
    ownerId: String = "user1",
    memberIds: List<String> = listOf(ownerId),
    invitationCode: String = "ABC123",
    createdAt: Instant = Instant.EPOCH
) = Group(
    id = id,
    name = name,
    memberIds = memberIds,
    ownerId = ownerId,
    invitationCode = invitationCode,
    workDays = DayOfWeek.entries.take(5),
    workStart = LocalTime.of(7, 0),
    workEnd = LocalTime.of(17, 0),
    createdAt = createdAt,
    updatedAt = createdAt
)

fun testEvent(
    id: String = "e1",
    title: String = "Sprint planning",
    createdAt: Instant = Instant.ofEpochSecond(1_000)
) = Event(
    id = id,
    title = title,
    startAt = createdAt.plusSeconds(3_600),
    endAt = createdAt.plusSeconds(7_200),
    createdAt = createdAt,
    updatedAt = createdAt
)

fun testAnnouncement(
    id: String = "a1",
    title: String = "Welcome",
    text: String = "Hello team",
    createdAt: Instant = Instant.ofEpochSecond(2_000)
) = Announcement(id = id, title = title, text = text, createdAt = createdAt)
