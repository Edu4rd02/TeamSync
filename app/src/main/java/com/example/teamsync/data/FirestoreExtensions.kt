package com.example.teamsync.data

import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

// A just-written serverTimestamp is null locally until the server confirms it, so estimate it.
fun DocumentSnapshot.instantOf(field: String): Instant {
    val timestamp = getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: return Instant.now()
    return Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())
}