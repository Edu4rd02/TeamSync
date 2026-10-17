package com.example.teamsync

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.teamsync.data.CalendarWriter
import com.example.teamsync.data.EventCalendarSync
import com.example.teamsync.data.FireStoreConstants
import com.example.teamsync.data.repository.EventRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String){
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection(FireStoreConstants.USERS_COLLECTION)
            .document(uid)
            .set(
                mapOf(FireStoreConstants.UsersFields.FCM_TOKENS to FieldValue.arrayUnion(token)),
                SetOptions.merge()
            )
            .addOnFailureListener { Log.e("MyFirebaseMessagingService","Failed to save new token",it) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
       when(message.data["type"]) {
           TYPE_GROUP_REMOVED -> removeGroupFromCalendar(message.data)
           else -> showNotification(message.data)
       }
    }

    // The user left or was removed from a group: drop its upcoming events from the device calendar.
    private fun removeGroupFromCalendar(data: Map<String, String>) {
        val groupId = data["groupId"] ?: return
        // The device could be signed in with a different account by now
        if (data["uid"] != Firebase.auth.currentUser?.uid) return

        // onMessageReceived already runs on a background thread and must finish its work before returning
        runBlocking {
            EventCalendarSync(EventRepository(), CalendarWriter(this@MyFirebaseMessagingService))
                .removeGroup(groupId)
        }
    }

    private fun showNotification(data: Map<String,String>){
        val title = data["title"] ?: return
        val body = data["body"] ?: ""
        val groupId = data["groupId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("groupId", groupId)
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "events_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val TYPE_GROUP_REMOVED = "group_removed"
    }
}