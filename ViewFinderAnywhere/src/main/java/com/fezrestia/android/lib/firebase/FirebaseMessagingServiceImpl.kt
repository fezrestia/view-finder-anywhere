package com.fezrestia.android.lib.firebase

import android.widget.Toast

import com.fezrestia.android.lib.util.log.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FirebaseMessagingServiceImpl : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (IS_DEBUG) Log.logDebug(TAG, "onMessageReceived : E")

        val notification = remoteMessage.notification
        val msg = if (notification != null) {
            notification.body ?: "RemoteMessage.Notification.Body is null"
        } else {
            "RemoteMessage.Notification is null"
        }

        //TODO: Handle message.
        Toast.makeText(
                this,
                "Msg = $msg",
                Toast.LENGTH_SHORT)
                .show()
        if (IS_DEBUG) {
            Log.logDebug(TAG, "### From = ${remoteMessage.from}")
            Log.logDebug(TAG, "### Msg  = $msg")
        }

        if (IS_DEBUG) Log.logDebug(TAG, "onMessageReceived : X")
    }

    companion object {
        const val TAG = "FirebaseMessagingServiceImpl"

        private const val IS_DEBUG = false or Log.IS_DEBUG
    }
}
