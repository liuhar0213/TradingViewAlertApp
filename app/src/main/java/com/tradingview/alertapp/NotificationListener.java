package com.tradingview.alertapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "TVAlertListener";
    private static final String CHANNEL_ID = "tv_alerts";
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotificationListener Service Created");
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannel();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text = extras.getString(Notification.EXTRA_TEXT, "");

        Log.d(TAG, "Notification from: " + packageName);
        Log.d(TAG, "Title: " + title);
        Log.d(TAG, "Text: " + text);

        // Filter for TradingView app or email apps with "TradingView" in content
        if (isTradingViewAlert(packageName, title, text)) {
            Log.i(TAG, "TradingView Alert Detected!");
            triggerAlert(title, text);
        }
    }

    private boolean isTradingViewAlert(String packageName, String title, String text) {
        // TradingView app package names
        if (packageName.contains("tradingview") ||
            packageName.contains("com.tradingview")) {
            return true;
        }

        // Email apps with TradingView in content
        String[] emailApps = {
            "com.google.android.gm",      // Gmail
            "com.microsoft.office.outlook", // Outlook
            "com.yahoo.mobile.client",     // Yahoo Mail
            "com.samsung.android.email"    // Samsung Email
        };

        for (String emailApp : emailApps) {
            if (packageName.equals(emailApp)) {
                String content = (title + " " + text).toLowerCase();
                if (content.contains("tradingview") ||
                    content.contains("alert") ||
                    content.contains("警报")) {
                    return true;
                }
            }
        }

        return false;
    }

    private void triggerAlert(String title, String text) {
        // Play alarm sound
        playAlarmSound();

        // Vibrate
        vibratePhone();

        // Show custom notification
        showAlertNotification(title, text);
    }

    private void playAlarmSound() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }

            // Use alarm sound
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            // Auto stop after 30 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(30000);
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            Log.e(TAG, "Error playing alarm sound", e);
        }
    }

    private void vibratePhone() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 1000, 500, 1000, 500, 1000};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, 0);
            }

            // Auto stop vibration after 30 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(30000);
                    if (vibrator != null) {
                        vibrator.cancel();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void showAlertNotification(String title, String text) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("TradingView Alert!")
            .setContentText(title + ": " + text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setFullScreenIntent(null, true);

        notificationManager.notify(1, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "TradingView Alerts";
            String description = "High priority alerts from TradingView";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Optional: handle notification removal
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        Log.d(TAG, "NotificationListener Service Destroyed");
    }
}
