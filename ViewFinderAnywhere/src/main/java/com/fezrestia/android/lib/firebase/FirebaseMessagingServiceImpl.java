package com.fezrestia.android.lib.firebase;

import android.widget.Toast;

import com.fezrestia.android.lib.util.log.Log;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Objects;

import androidx.annotation.NonNull;

public class FirebaseMessagingServiceImpl extends FirebaseMessagingService {
    public static final String TAG = "FirebaseMessagingServiceImpl";

    @SuppressWarnings("PointlessBooleanExpression")
    private static final boolean IS_DEBUG = false | Log.IS_DEBUG;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        if (IS_DEBUG) Log.logDebug(TAG, "onMessageReceived : E");

        //TODO: Handle message.
        Toast.makeText(
                this,
                "Msg = " + Objects.requireNonNull(remoteMessage.getNotification()).getBody(),
                Toast.LENGTH_SHORT)
                .show();
        if (IS_DEBUG) {
            Log.logDebug(TAG, "### From = " + remoteMessage.getFrom());
            Log.logDebug(TAG, "### Msg  = " + Objects.requireNonNull(remoteMessage.getNotification()).getBody());
        }

        if (IS_DEBUG) Log.logDebug(TAG, "onMessageReceived : X");
    }
}
