package com.example.teamsync.data.repository

import android.util.Log
import com.example.teamsync.data.FireStoreConstants
import com.example.teamsync.data.FireStoreConstants.EVENTS_COLLECTION
import com.example.teamsync.data.FireStoreConstants.EventFields
import com.example.teamsync.data.instantOf
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.EventStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.collections.orEmpty

class EventRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun events (groupId: String) = firestore.collection(FireStoreConstants.GROUPS_COLLECTION)
        .document(groupId)
        .collection(EVENTS_COLLECTION)
    suspend fun createEvent(
        groupId: String,
        title: String,
        link: String,
        startAt: Timestamp,
        endAt: Timestamp
    ){
        val newDocument = events(groupId).document()

        newDocument.set(mapOf(
            EventFields.TITLE to title,
            EventFields.LINK to link,
            EventFields.START_AT to startAt,
            EventFields.END_AT to endAt,
            EventFields.CREATED_AT to FieldValue.serverTimestamp(),
            EventFields.UPDATED_AT to FieldValue.serverTimestamp(),
            EventFields.STATUS to EventStatus.SCHEDULED.value
        )).await()
    }

    fun getEvents(groupId: String): Flow<List<Event>> = callbackFlow {
        val registration = events(groupId)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Error listening to group $groupId", exception)
                    close(exception)
                    return@addSnapshotListener
                }

                val list = snapshot?.documents
                    ?.mapNotNull { it.toEvent() }
                    ?.sortedByDescending { it.createdAt }
                    .orEmpty()

                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toEvent(): Event? {
        return try {
            Event(
                id = id,
                title = getString(EventFields.TITLE) ?: return null,
                startAt = instantOf(EventFields.START_AT),
                endAt = instantOf(EventFields.END_AT),
                createdAt = instantOf(EventFields.CREATED_AT),
                updatedAt = instantOf(EventFields.UPDATED_AT),
                status = EventStatus.fromValue(getString(EventFields.STATUS))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Skipping malformed event $id", e)
            null
        }
    }

    private companion object {
        const val TAG = "EventRepository"
    }
}