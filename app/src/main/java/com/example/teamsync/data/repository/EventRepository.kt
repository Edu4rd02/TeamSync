package com.example.teamsync.data.repository

import android.util.Log
import com.example.teamsync.data.FireStoreConstants
import com.example.teamsync.data.FireStoreConstants.ConfirmationFields
import com.example.teamsync.data.FireStoreConstants.EVENTS_COLLECTION
import com.example.teamsync.data.FireStoreConstants.EventFields
import com.example.teamsync.data.instantOf
import com.example.teamsync.data.instantOrNull
import com.example.teamsync.data.model.Confirmation
import com.example.teamsync.data.model.ConfirmationStatus
import com.example.teamsync.data.model.Event
import com.example.teamsync.data.model.EventStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
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

    private fun confirmations(groupId: String, eventId: String) =
        events(groupId).document(eventId).collection(FireStoreConstants.CONFIRMATIONS_COLLECTION)

    // The members in [confirmedIds] were free at that time, so they start as confirmed (automatically)
    suspend fun createEvent(
        groupId: String,
        title: String,
        link: String,
        startAt: Timestamp,
        endAt: Timestamp,
        confirmedIds: Collection<String> = emptyList()
    ){
        val newDocument = events(groupId).document()

        // The event and its confirmations are written together
        firestore.runBatch { batch ->
            batch.set(newDocument, mapOf(
                EventFields.TITLE to title,
                EventFields.LINK to link,
                EventFields.START_AT to startAt,
                EventFields.END_AT to endAt,
                EventFields.CREATED_AT to FieldValue.serverTimestamp(),
                EventFields.UPDATED_AT to FieldValue.serverTimestamp(),
                EventFields.STATUS to EventStatus.SCHEDULED.value
            ))
            batch.setAutomaticConfirmations(newDocument, confirmedIds)
        }.await()
    }

    // Only the title and link change when [startAt]/[endAt] are null. A new time resets the
    // confirmations: everyone goes back to waiting except [confirmedIds], who are free at the new time.
    suspend fun updateEvent(
        groupId: String,
        eventId: String,
        title: String,
        link: String,
        startAt: Timestamp? = null,
        endAt: Timestamp? = null,
        confirmedIds: Collection<String> = emptyList()
    ) {
        val document = events(groupId).document(eventId)
        val fields = mutableMapOf<String, Any>(
            EventFields.TITLE to title,
            EventFields.LINK to link,
            EventFields.UPDATED_AT to FieldValue.serverTimestamp()
        )

        if (startAt == null || endAt == null) {
            document.update(fields).await()
            return
        }

        fields[EventFields.START_AT] = startAt
        fields[EventFields.END_AT] = endAt
        val previous = confirmations(groupId, eventId).get().await().documents
        firestore.runBatch { batch ->
            batch.update(document, fields)
            previous.forEach { batch.delete(it.reference) }
            batch.setAutomaticConfirmations(document, confirmedIds)
        }.await()
    }

    // Soft delete: the document stays so every device can see it was cancelled and clean its calendar
    suspend fun cancelEvent(groupId: String, eventId: String) {
        events(groupId).document(eventId).update(
            EventFields.STATUS, EventStatus.CANCELLED.value,
            EventFields.UPDATED_AT, FieldValue.serverTimestamp()
        ).await()
    }

    private fun WriteBatch.setAutomaticConfirmations(event: DocumentReference, userIds: Collection<String>) {
        userIds.forEach { uid ->
            set(
                event.collection(FireStoreConstants.CONFIRMATIONS_COLLECTION).document(uid),
                mapOf(
                    ConfirmationFields.USER_ID to uid,
                    ConfirmationFields.STATUS to ConfirmationStatus.CONFIRMED.value,
                    // No answer from the member yet: this is what marks it as automatic
                    ConfirmationFields.RESPONDED_AT to null
                )
            )
        }
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

    fun getEvent(groupId: String, eventId: String): Flow<Event?> = callbackFlow {
        val registration = events(groupId).document(eventId)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Error listening to event $eventId", exception)
                    close(exception)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toEvent())
            }
        awaitClose { registration.remove() }
    }

    // One-shot reads, used by the device calendar reconciliation and the edit screen
    suspend fun getEventsOnce(groupId: String): List<Event> =
        events(groupId).get().await().documents.mapNotNull { it.toEvent() }

    suspend fun getEventOnce(groupId: String, eventId: String): Event? =
        events(groupId).document(eventId).get().await().takeIf { it.exists() }?.toEvent()

    fun getConfirmations(groupId: String, eventId: String): Flow<List<Confirmation>> = callbackFlow {
        val registration = confirmations(groupId, eventId)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Error listening to confirmations of $eventId", exception)
                    close(exception)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toConfirmation() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    // Reads if the user confirm or reject the assistance to the event
    //If it's null, is waiting
    suspend fun getConfirmationOnce(groupId: String, eventId: String, userId: String): Confirmation? =
        confirmations(groupId, eventId).document(userId).get().await()
            .takeIf { it.exists() }
            ?.toConfirmation()

    // Store the user confirmation
    suspend fun respond(groupId: String, eventId: String, userId: String, status: ConfirmationStatus) {
        confirmations(groupId, eventId).document(userId).set(
            mapOf(
                ConfirmationFields.USER_ID to userId,
                ConfirmationFields.STATUS to status.value,
                ConfirmationFields.RESPONDED_AT to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // Back to waiting, used when an automatic confirmation clashes with the member's calendar
    suspend fun deleteConfirmation(groupId: String, eventId: String, userId: String) {
        confirmations(groupId, eventId).document(userId).delete().await()
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
                link = getString(EventFields.LINK)?.takeIf { it.isNotBlank() },
                status = EventStatus.fromValue(getString(EventFields.STATUS))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Skipping malformed event $id", e)
            null
        }
    }

    private fun DocumentSnapshot.toConfirmation(): Confirmation? {
        val status = ConfirmationStatus.fromValue(getString(ConfirmationFields.STATUS)) ?: return null
        return Confirmation(
            uid = getString(ConfirmationFields.USER_ID) ?: id,
            status = status,
            respondedAt = instantOrNull(ConfirmationFields.RESPONDED_AT)
        )
    }

    private companion object {
        const val TAG = "EventRepository"
    }
}
