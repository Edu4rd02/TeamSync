package com.example.teamsync.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.teamsync.data.model.TimeSlot

// Reads the user's busy time from the device calendar (Calendar Provider).
class CalendarDataSource(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    // One query per window, so nothing outside the given windows is ever requested.
    // Blocking call: run it off the main thread.
    fun busyBlocks(windows: List<Pair<Long, Long>>): List<TimeSlot> =
        windows.flatMap { (from, to) -> query(from, to) }
            .sortedBy { it.start }
            .merge()

    private fun query(from: Long, to: Long): List<TimeSlot> {
        // Build the URI to see instances in a determined range of time
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .also {
                ContentUris.appendId(it, from)
                ContentUris.appendId(it, to)
            }
            .build()
        // Columns that will be selected from all the information requested
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val slots = mutableListOf<TimeSlot>()

        // Create like a SQL query, URI (from), projection (SELECT)
        // Return a cursor that contains all the information from that query
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                // The third column (0,1,2) is for ALL_DAY events, if is 1 is an all day event
                // All-day events are reported in UTC and would be shifted; ignored for now.
                if (cursor.getInt(2) == 1) continue

                slots += TimeSlot(
                    // Compared the window and the event time, so can cut times
                    start = maxOf(cursor.getLong(0), from),
                    end = minOf(cursor.getLong(1), to)
                )
            }
        }
        return slots
    }

    // Joins overlapping or touching slots. Expects the list sorted by start.
    private fun List<TimeSlot>.merge(): List<TimeSlot> {
        val merged = mutableListOf<TimeSlot>()
        for (slot in this) {
            val last = merged.lastOrNull()
            if (last != null && slot.start <= last.end) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, slot.end))
            } else {
                merged += slot
            }
        }
        return merged
    }
}
