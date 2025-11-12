package com.tradingview.alertapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AlertPollingService extends Service {
    private static final String TAG = "AlertPollingService";
    private static final String CHANNEL_ID = "alert_polling";
    private static final int NOTIFICATION_ID = 100;
    private static final long POLL_INTERVAL = 5000; // 5 seconds

    private Handler handler;
    private Runnable pollingRunnable;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    // TODO: Replace with your server URL
    private static final String SERVER_URL = "http://10.0.0.170:80/poll";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AlertPollingService Created");

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createForegroundNotification());

        // Start polling
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                pollForAlerts();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.post(pollingRunnable);

        Log.i(TAG, "Polling started");
    }

    private void pollForAlerts() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Parse JSON response
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray alerts = jsonResponse.getJSONArray("alerts");
                    int count = jsonResponse.getInt("count");

                    if (count > 0) {
                        Log.i(TAG, "Received " + count + " alert(s)");

                        // Trigger alarm for each alert
                        for (int i = 0; i < alerts.length(); i++) {
                            JSONObject alert = alerts.getJSONObject(i);
                            String subject = alert.getString("subject");
                            String from = alert.optString("from", "");

                            Log.i(TAG, "Alert: " + subject + " from " + from);
                            triggerAlert(subject, from);
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error polling for alerts", e);
            }
        }).start();
    }

    private void triggerAlert(String title, String message) {
        Log.i(TAG, "Triggering alert: " + title);

        // Play alarm sound
        playAlarmSound();

        // Vibrate
        vibratePhone();

        // Show notification
        showAlertNotification(title, message);
    }

    private void playAlarmSound() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }

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

    private void showAlertNotification(String title, String message) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "tv_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("TradingView Alert!")
            .setContentText(title + ": " + message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private Notification createForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("TradingView Alert Monitor")
            .setContentText("Monitoring for alerts...")
            .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for foreground service
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Alert Polling Service",
                NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Background service for polling TradingView alerts");

            // Channel for alert notifications
            NotificationChannel alertChannel = new NotificationChannel(
                "tv_alerts",
                "TradingView Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("High priority alerts from TradingView");
            alertChannel.enableVibration(true);
            alertChannel.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(alertChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && pollingRunnable != null) {
            handler.removeCallbacks(pollingRunnable);
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        Log.d(TAG, "AlertPollingService Destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
