package com.example.teamsync.data.repository

import com.example.teamsync.data.FireStoreConstants
import com.example.teamsync.data.FireStoreConstants.EventFields
import com.example.teamsync.data.model.EventStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EventRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun createEvent(
        groupId: String,
        title: String,
        link: String,
        startAt: Timestamp,
        endAt: Timestamp
    ){
        val newDocument = firestore.collection(FireStoreConstants.GROUPS_COLLECTION)
            .document(groupId)
            .collection(FireStoreConstants.EVENTS_COLLECTION).document()

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
}