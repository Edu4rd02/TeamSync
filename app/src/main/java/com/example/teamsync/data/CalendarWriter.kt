package com.example.teamsync.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import java.time.ZoneId

// What TeamSync writes for an event in the device calendar (times in epoch millis).
data class CalendarEntry(
    val title: String,
    val description: String?,
    val start: Long,
    val end: Long
)

data class StoredCalendarEntry(val id: Long, val entry: CalendarEntry)

class CalendarWriter(private val context: Context) {

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // Search the event in the device's calendar
    fun findEntry(uri: String): StoredCalendarEntry? {
        // List of columns to return
        val projection = arrayOf(Events._ID, Events.TITLE, Events.DESCRIPTION, Events.DTSTART, Events.DTEND)
        // A deleted entry stays (DELETED = 1) until the calendar's sync adapter removes it
        val selection = "${Events.CUSTOM_APP_URI} = ? AND ${Events.DELETED} = 0"
        context.contentResolver.query(Events.CONTENT_URI, projection, selection, arrayOf(uri), null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                return StoredCalendarEntry(
                    id = cursor.getLong(0),
                    entry = CalendarEntry(
                        title = cursor.getString(1).orEmpty(),
                        description = cursor.getString(2)?.takeIf { it.isNotBlank() },
                        start = cursor.getLong(3),
                        end = cursor.getLong(4)
                    )
                )
            }
        return null
    }

    // Returns false if there is no calendar where the user can write.
    fun insert(uri: String, entry: CalendarEntry): Boolean {
        val calendarId = writableCalendarId() ?: return false
        val values = entry.toValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(Events.CUSTOM_APP_URI, uri)
        }
        return context.contentResolver.insert(Events.CONTENT_URI, values) != null
    }

    fun update(id: Long, entry: CalendarEntry): Boolean =
        context.contentResolver.update(eventUri(id), entry.toValues(), null, null) > 0

    fun delete(id: Long): Boolean =
        context.contentResolver.delete(eventUri(id), null, null) > 0

    // True if any other event of the calendar overlaps [from, to)
    fun hasConflict(from: Long, to: Long, excludeUri: String): Boolean {
        val uri = Instances.CONTENT_URI.buildUpon()
            .also {
                ContentUris.appendId(it, from)
                ContentUris.appendId(it, to)
            }
            .build()
        val projection = arrayOf(Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.CUSTOM_APP_URI)

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getInt(2) == 1) continue
                if (cursor.getString(3) == excludeUri) continue
                // Events that only touch the edges (one ends when the other starts) don't clash
                if (cursor.getLong(0) < to && cursor.getLong(1) > from) return true
            }
        }
        return false
    }

    // Function to get pairs of (event ID, custom URI) for events created by this app (that are not deleted).
    fun entryIds(uriPrefix: String?, endsAfter: Long): List<Pair<Long, String>> {
        val projection = arrayOf(Events._ID, Events.CUSTOM_APP_URI)
        var selection = "${Events.CUSTOM_APP_PACKAGE} = ? AND ${Events.DELETED} = 0 AND ${Events.DTEND} > ?"
        val args = mutableListOf(context.packageName, endsAfter.toString())
        if (uriPrefix != null) {
            selection += " AND ${Events.CUSTOM_APP_URI} LIKE ?"
            args += "$uriPrefix%"
        }

        val entries = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(Events.CONTENT_URI, projection, selection, args.toTypedArray(), null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    entries.add(Pair(cursor.getLong(0), cursor.getString(1)))
                }
            }
        return entries
    }

    // A visible calendar the user can add events to, the primary one first.
    private fun writableCalendarId(): Long? {
        val selection = "${Calendars.VISIBLE} = 1 AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val order = "${Calendars.IS_PRIMARY} DESC, ${Calendars._ID} ASC"
        context.contentResolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID), selection, args, order)
            ?.use { cursor -> if (cursor.moveToFirst()) return cursor.getLong(0) }
        return null
    }

    private fun eventUri(id: Long) = ContentUris.withAppendedId(Events.CONTENT_URI, id)

    private fun CalendarEntry.toValues() = ContentValues().apply {
        put(Events.TITLE, title)
        put(Events.DESCRIPTION, description)
        put(Events.DTSTART, start)
        put(Events.DTEND, end)
    }

    private companion object {
        val PERMISSIONS = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    }
}
