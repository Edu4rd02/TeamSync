package com.example.teamsync.data

import com.example.teamsync.data.repository.GroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Reads the device calendar inside the group's work windows and uploads the busy blocks.
class CalendarSync(
    private val groupRepository: GroupRepository,
    private val calendarDataSource: CalendarDataSource
) {
    // Avoids two syncs running at once (e.g. entering and leaving a group quickly)
    private val mutex = Mutex()

    // Function to read all the event from the calendar user's device
    suspend fun sync(groupId: String, userId: String, throttle: Boolean = true) {
        if (!calendarDataSource.hasPermission()) return
        mutex.withLock {
            if (throttle && !isStale(groupId, userId)) return

            val group = groupRepository.getGroupOnce(groupId) ?: return

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val now = System.currentTimeMillis()

            // Create the windows of work
            val windows = (0 until SYNC_DAYS)
                // Create a list of pairs where indicates the start and end of the window
                .mapNotNull { group.windowFor(today.plusDays(it.toLong()), zone) }
                // Skip a window that already ended today
                .filter { (_, end) -> end > now }
            if (windows.isEmpty()) return

            val blocks = withContext(Dispatchers.IO) { calendarDataSource.busyBlocks(windows) }
            groupRepository.syncUserCalendar(
                userId = userId,
                groupId = groupId,
                blocks = blocks,
                windowStart = windows.first().first,
                windowEnd = windows.last().second
            )
        }
    }

    // Returns true if the member's last sync time is older than MIN_SYNC_INTERVAL or missing.
    private suspend fun isStale(groupId: String, userId: String): Boolean {
        val member = groupRepository.getMemberOnce(groupId, userId) ?: return false
        val lastSync = member.lastSyncAt ?: return true
        return Duration.between(lastSync, Instant.now()) >= MIN_SYNC_INTERVAL
    }

    companion object {
        const val SYNC_DAYS = 45
        private val MIN_SYNC_INTERVAL = Duration.ofMinutes(2)
    }
}
