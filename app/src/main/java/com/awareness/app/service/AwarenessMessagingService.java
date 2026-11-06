package com.awareness.app.service;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.awareness.app.R;
import com.awareness.app.ui.auth.SplashActivity;
import com.awareness.app.utils.Constants;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives push notifications (Daily Quiz reminders).
 *
 * Notes:
 *  - When the app is in the background and the message has a "notification" payload,
 *    Android shows it automatically and this method is NOT called. It is called for
 *    foreground messages and for data-only messages.
 *  - Tapping the notification opens SplashActivity with destination=quiz; Splash forwards it and
 *    MainActivity opens the Daily Quiz.
 *  - For notifications sent from the Firebase Console while the app is closed (Android shows those
 *    itself), add a custom data field  destination = quiz  so the tap deep-links the same way.
 *  - The app subscribes to the "daily_quiz" topic (see AwarenessApp), so we do not need
 *    to store per-user FCM tokens -> onNewToken needs no work.
 */
public class AwarenessMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String title = "Awareness";
        String body  = "Your daily scam-awareness challenge is ready!";

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody()  != null) body  = message.getNotification().getBody();
        } else {
            if (message.getData().get("title") != null) title = message.getData().get("title");
            if (message.getData().get("body")  != null) body  = message.getData().get("body");
        }
        showNotification(title, body);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // Nothing to do: topic subscriptions survive token refreshes.
    }

    private void showNotification(String title, String body) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        // Android 13+: the user may have denied POST_NOTIFICATIONS -> silently skip
        if (!manager.areNotificationsEnabled()) return;

        Intent intent = new Intent(this, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("destination", "quiz");   // Daily Quiz reminder -> open the quiz
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_shield)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pending)
                        .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
