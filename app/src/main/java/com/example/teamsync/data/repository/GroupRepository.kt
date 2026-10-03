package com.example.teamsync.data.repository

import android.util.Log
import com.example.teamsync.data.FireStoreConstants
import com.example.teamsync.data.FireStoreConstants.GroupFields
import com.example.teamsync.data.FireStoreConstants.MemberFields
import com.example.teamsync.data.model.CalendarStatus
import com.example.teamsync.data.model.Group
import com.example.teamsync.data.model.MemberRole
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.concurrent.CancellationException

class GroupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val groups = firestore.collection(FireStoreConstants.GROUPS_COLLECTION)

    fun getMyGroups(userId: String): Flow<List<Group>> = callbackFlow {
        val registration = groups
            .whereArrayContains(GroupFields.MEMBER_IDS, userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to groups", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toGroup() }
                    ?.sortedByDescending { it.createdAt }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun createGroup(
        name: String,
        description: String?,
        ownerId: String,
        ownerDisplayName: String?,
        ownerPhotoUrl: String?,
        workDays: List<DayOfWeek>,
        workStart: LocalTime,
        workEnd: LocalTime
    ) {
        val document = groups.document()
        val invitationCode = newInvitationCode()
        val data = mapOf(
            GroupFields.NAME to name,
            GroupFields.DESCRIPTION to description,
            GroupFields.INVITATION_CODE to invitationCode,
            GroupFields.OWNER_ID to ownerId,
            GroupFields.WORK_DAYS to workDays.map { it.name },
            GroupFields.WORK_START to workStart.toString(),
            GroupFields.WORK_END to workEnd.toString(),
            GroupFields.CREATED_AT to FieldValue.serverTimestamp(),
            GroupFields.UPDATED_AT to FieldValue.serverTimestamp(),
            GroupFields.MEMBER_IDS to listOf(ownerId)
        )
        val adminMember = mapOf(
            MemberFields.ROLE to MemberRole.ADMIN.name,
            MemberFields.DISPLAY_NAME to ownerDisplayName,
            MemberFields.PHOTO_URL to ownerPhotoUrl,
            MemberFields.JOINED_AT to FieldValue.serverTimestamp(),
            MemberFields.LAST_SYNC_AT to null,
            MemberFields.CALENDAR_STATUS to CalendarStatus.NOT_CONNECTED.name
        )

        try {
            // Run a transaction so, if the user is offline it fails instead of queueing a write
            // and create duplicate groups
            firestore.runTransaction { transaction ->
                transaction.set(document, data)
                transaction.set(
                    document.collection(FireStoreConstants.MEMBERS_COLLECTION).document(ownerId),
                    adminMember
                )
                null
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group", e)
            throw e
        }
    }

    // Returns null (and logs) if the document is missing fields or has a malformed one.
    private fun DocumentSnapshot.toGroup(): Group? {
        return try {
            Group(
                id = id,
                name = getString(GroupFields.NAME) ?: return null,
                description = getString(GroupFields.DESCRIPTION),
                memberIds = (get(GroupFields.MEMBER_IDS) as? List<*>)
                    ?.filterIsInstance<String>().orEmpty(),
                ownerId = getString(GroupFields.OWNER_ID) ?: return null,
                invitationCode = getString(GroupFields.INVITATION_CODE).orEmpty(),
                workDays = (get(GroupFields.WORK_DAYS) as? List<*>)
                    ?.filterIsInstance<String>()
                    ?.map { DayOfWeek.valueOf(it) }
                    .orEmpty(),
                workStart = LocalTime.parse(getString(GroupFields.WORK_START) ?: return null),
                workEnd = LocalTime.parse(getString(GroupFields.WORK_END) ?: return null),
                createdAt = instantOf(GroupFields.CREATED_AT),
                updatedAt = instantOf(GroupFields.UPDATED_AT)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Skipping malformed group $id", e)
            null
        }
    }

    suspend fun joinGroup(
        invitationCode: String,
        userId: String,
        displayName: String?,
        photoUrl: String?
    ): Result<Unit> {
        return try {
            val querySnapshot = groups
                .whereEqualTo(GroupFields.INVITATION_CODE, invitationCode)
                .limit(1)
                .get()
                .await()

            // When a group is not found, no exception is thrown, so return failure manually
            val groupDoc = querySnapshot.documents.firstOrNull()
                ?: return Result.failure(NoSuchElementException("Group not found with code: $invitationCode"))

            val member = mapOf(
                MemberFields.ROLE to MemberRole.MEMBER.name,
                MemberFields.DISPLAY_NAME to displayName,
                MemberFields.PHOTO_URL to photoUrl,
                MemberFields.JOINED_AT to FieldValue.serverTimestamp(),
                MemberFields.LAST_SYNC_AT to null,
                MemberFields.CALENDAR_STATUS to CalendarStatus.NOT_CONNECTED.name
            )

            firestore.runTransaction { transaction ->
                transaction.update(groupDoc.reference, GroupFields.MEMBER_IDS, FieldValue.arrayUnion(userId))
                transaction.set(groupDoc.reference.collection(FireStoreConstants.MEMBERS_COLLECTION).document(userId), member)
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error joining group", e)
            Result.failure(e)
        }
    }

    // A just-written serverTimestamp is null locally until the server confirms it, so estimate it.
    private fun DocumentSnapshot.instantOf(field: String): Instant {
        val timestamp = getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: return Instant.now()
        return Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())
    }

    private companion object {
        const val TAG = "GroupRepository"
        const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O or 1/I lookalikes

        fun newInvitationCode(): String = (1..6).map { CODE_CHARS.random() }.joinToString("")
    }
}
