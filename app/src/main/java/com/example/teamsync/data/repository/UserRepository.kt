package com.example.teamsync.data.repository

import com.example.teamsync.data.FireStoreConstants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val users = firestore.collection(FireStoreConstants.USERS_COLLECTION)
    suspend fun registerFcmToken(uid: String): Result<Unit> = runCatching {
        val token = FirebaseMessaging.getInstance().token.await()
        users
            .document(uid)
            .set(mapOf(
                FireStoreConstants.UsersFields.FCM_TOKENS to FieldValue.arrayUnion(token)),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun removeToken(uid: String): Result<Unit> = runCatching{
        val token = FirebaseMessaging.getInstance().token.await()
        users
            .document(uid)
            .set(
                mapOf(FireStoreConstants.UsersFields.FCM_TOKENS to FieldValue.arrayRemove(token)),
                SetOptions.merge()
            ).await()
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}