package com.example.teamsync.data

import com.example.teamsync.data.model.Group
import com.example.teamsync.data.model.TimeSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val STEP_MS = 30 * 60 * 1000L

data class MeetingOption(
    val slot: TimeSlot,
    val attendees: List<String>,
    val missing: List<String>
)

// The options plus how many members were taken into account (synced calendar) and how many weren't.
data class FreeBlocksResult(
    val options: List<MeetingOption>,
    val syncedCount: Int,
    val unsyncedCount: Int,
    // Display name of every member by id, so the options (which carry ids) can be shown with names
    val names: Map<String, String> = emptyMap(),
    // Busy time of every synced member, to know exactly who is free at the start finally chosen
    val busyByUser: Map<String, List<TimeSlot>> = emptyMap()
)

// Returns the meeting options inside the group's work windows
fun findFreeBlocks(
    group: Group,
    busyByUser: Map<String, List<TimeSlot>>,
    range: Pair<LocalDate, LocalDate>,
    durationMs: Long,
    zone: ZoneId,
    preferredHours: ClosedRange<LocalTime>? = null,
    blocked: List<TimeSlot> = emptyList(),
    now: Long = System.currentTimeMillis()
): List<MeetingOption> {

    // If there are preferredHours check if are valid
    if (preferredHours != null) {
        require(preferredHours.start <= preferredHours.endInclusive) {
            "The start hour can't be after the end hour"
        }
        require(preferredHours.start >= group.workStart && preferredHours.endInclusive <= group.workEnd) {
            "The preferred hours must be inside the group's work hours"
        }
    }

    // Generate range days
    val dates = generateSequence(range.first) { it.plusDays(1) }
        .takeWhile { !it.isAfter(range.second) }
        .toList()

    val options = mutableListOf<MeetingOption>()

    for (date in dates) {
        // Skip days that aren't work days
        val window = group.windowFor(date, zone) ?: continue

        // Narrow the window to the preferred hours (already validated to fit inside it)
        val from = preferredHours?.let { date.atTime(it.start).atZone(zone).toInstant().toEpochMilli() }
            ?: window.first
        val end = preferredHours?.let { date.atTime(it.endInclusive).atZone(zone).toInstant().toEpochMilli() }
            ?: window.second

        // Don't offer hours that already passed, rounded up to the next step
        val start = if (now > from) roundUp(now, STEP_MS) else from

        // Only the busy blocks that touch this day's window matter
        val dayBusy = busyByUser.mapValues { (_, blocks) ->
            blocks.filter { it.start < end && it.end > start }
        }

        // Filter the blocked time slots that overlap with this day's window
        val eventBlock = blocked.filter { it.start < end && it.end > start }

        var s = start
        while (s + durationMs <= end) {
            val e = s + durationMs
            if (isFree(eventBlock, s, e)) {
                val (free, busy) = dayBusy.keys.partition { id -> isFree(dayBusy[id].orEmpty(), s, e) }
                if (free.isNotEmpty()) options += MeetingOption(TimeSlot(s, e), free, busy)
            }
            s += STEP_MS
        }
    }

    return options.sortedWith(
        compareByDescending<MeetingOption> { it.attendees.size }.thenBy { it.slot.start }
    )
}

fun isFree(busy: List<TimeSlot>, start: Long, end: Long) =
    busy.none { end > it.start && start < it.end }

private fun roundUp(value: Long, step: Long) = (value + step - 1) / step * step
