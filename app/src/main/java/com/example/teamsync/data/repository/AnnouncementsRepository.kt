package com.example.teamsync.data.repository

import android.util.Log
import com.example.teamsync.data.FireStoreConstants
import com.example.teamsync.data.FireStoreConstants.ANNOUNCEMENTS_COLLECTION
import com.example.teamsync.data.FireStoreConstants.AnnouncementFields
import com.example.teamsync.data.instantOf
import com.example.teamsync.data.model.Announcement
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AnnouncementsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun announcements(groupId: String) = firestore.collection(FireStoreConstants.GROUPS_COLLECTION)
        .document(groupId)
        .collection(ANNOUNCEMENTS_COLLECTION)

    suspend fun createAnnouncement(groupId: String, title: String, text: String) {
        announcements(groupId).document().set(
            mapOf(
                AnnouncementFields.TITLE to title,
                AnnouncementFields.TEXT to text,
                AnnouncementFields.CREATED_AT to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun getAnnouncements(groupId: String): Flow<List<Announcement>> = callbackFlow {
        val registration = announcements(groupId)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e(TAG, "Error listening to announcements of group $groupId", exception)
                    close(exception)
                    return@addSnapshotListener
                }

                val list = snapshot?.documents
                    ?.mapNotNull { it.toAnnouncement() }
                    ?.sortedByDescending { it.createdAt }
                    .orEmpty()

                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toAnnouncement(): Announcement? {
        return try {
            Announcement(
                id = id,
                title = getString(AnnouncementFields.TITLE) ?: return null,
                text = getString(AnnouncementFields.TEXT).orEmpty(),
                createdAt = instantOf(AnnouncementFields.CREATED_AT)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Skipping malformed announcement $id", e)
            null
        }
    }

    private companion object {
        const val TAG = "AnnouncementsRepository"
    }
}
