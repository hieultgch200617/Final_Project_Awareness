package com.awareness.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.awareness.app.utils.Constants;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class AwarenessApp extends Application {

    private static final String DAILY_QUIZ_TOPIC = "daily_quiz";

    @Override
    public void onCreate() {
        super.onCreate();

        // The app's colours are designed for a light UI only (see Theme.Awareness)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        FirebaseApp.initializeApp(this);
        createNotificationChannel();

        // Everyone gets the Daily Quiz reminder. Send it from Firebase Console ->
        // Messaging -> target "Topic: daily_quiz".
        FirebaseMessaging.getInstance().subscribeToTopic(DAILY_QUIZ_TOPIC);
    }

    /** Channels are required on Android 8+ (our minSdk is 26, so no version check needed). */
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                Constants.NOTIF_CHANNEL_ID,
                Constants.NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Daily scam-awareness quiz reminders");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
