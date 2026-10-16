package com.example.teamsync.data

import android.util.Log
import com.example.teamsync.data.model.Confirmation
import com.example.teamsync.data.model.ConfirmationStatus
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.EventStatus
import com.example.teamsync.data.repository.EventRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

data class ReconcileResult(
    // Something was written: a calendar entry or the user's confirmation
    val changed: Boolean = false,
    val inCalendar: Boolean = false,
    // The automatic confirmation was removed because it clashed with the user's calendar
    val revoked: Boolean = false
)

class EventCalendarSync(
    private val eventRepository: EventRepository,
    private val calendar: CalendarWriter,
    // To run operations outside the main thread
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // A reconciliation of the group and one of a single event could otherwise insert the same entry twice
    private val mutex = Mutex()

    // Reconciles every upcoming event of the group. Returns true if something changed.
    suspend fun reconcileGroup(groupId: String, userId: String): Boolean {
        if (!calendar.hasPermission()) return false
        return try {
            val now = Instant.now()
            eventRepository.getEventsOnce(groupId)
                .filter { it.endAt > now }
                .map { event ->
                    val confirmation = eventRepository.getConfirmationOnce(groupId, event.id, userId)
                    reconcileEvent(groupId, event, userId, confirmation).changed
                }
                .any { it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling the calendar of group $groupId", e)
            false
        }
    }

    suspend fun reconcileEvent(
        groupId: String,
        event: Event,
        userId: String,
        confirmation: Confirmation?
    ): ReconcileResult {
        if (!calendar.hasPermission()) return ReconcileResult()
        val uri = entryUri(groupId, event.id)

        return try {
            mutex.withLock {
                //  Search the event
                val stored = io { calendar.findEntry(uri) }
                val shouldBeInCalendar = event.status == EventStatus.SCHEDULED &&
                    event.endAt > Instant.now() &&
                    confirmation?.status == ConfirmationStatus.CONFIRMED

                // Rejected, waiting or cancelled: remove it if it was added before
                if (!shouldBeInCalendar) {
                    val deleted = stored != null && io { calendar.delete(stored.id) }
                    return@withLock ReconcileResult(changed = deleted)
                }

                val wanted = event.toCalendarEntry()

                // Confirms that the event from the device's calendar is the same from the database
                if (stored?.entry == wanted) return@withLock ReconcileResult(inCalendar = true)

                // The admin confirmed the user for them; if the calendar says they are busy, they go
                // back to waiting. A confirmation the user gave by hand is never undone.
                val start = event.startAt.toEpochMilli()
                val end = event.endAt.toEpochMilli()

                // If the user was automatically confirmed, but a conflict is now present,
                // revoke the confirmation and remove the entry from the calendar.
                if (confirmation.isAutomatic && io { calendar.hasConflict(start, end, uri) }) {
                    eventRepository.deleteConfirmation(groupId, event.id, userId)
                    if (stored != null) io { calendar.delete(stored.id) }
                    return@withLock ReconcileResult(changed = true, revoked = true)
                }

                // If the event already exists, update it, otherwise, insert it
                val written = io {
                    if (stored == null) calendar.insert(uri, wanted) else calendar.update(stored.id, wanted)
                }
                ReconcileResult(changed = written, inCalendar = written)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling event ${event.id}", e)
            ReconcileResult()
        }
    }

    // True if the user has something else in their calendar during the event.
    suspend fun hasConflict(groupId: String, event: Event): Boolean {
        if (!calendar.hasPermission()) return false
        return io {
            calendar.hasConflict(
                event.startAt.toEpochMilli(),
                event.endAt.toEpochMilli(),
                entryUri(groupId, event.id)
            )
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(ioDispatcher) { block() }

    private fun Event.toCalendarEntry() = CalendarEntry(
        title = title,
        description = link,
        start = startAt.toEpochMilli(),
        end = endAt.toEpochMilli()
    )

    companion object {
        private const val TAG = "EventCalendarSync"

        fun entryUri(groupId: String, eventId: String) = "teamsync://groups/$groupId/events/$eventId"
    }
}
