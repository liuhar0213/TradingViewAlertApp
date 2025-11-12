package com.tradingview.alertapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "TVAlertListener";
    private static final String CHANNEL_ID = "tv_alerts";
    private static final String TEST_ALERT_ACTION = "com.tradingview.alertapp.TEST_ALERT";
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private BroadcastReceiver testAlertReceiver;

    // 防止重复报警：记录已处理的通知
    private final java.util.Set<String> processedNotifications = new java.util.HashSet<>();
    private static final long NOTIFICATION_COOLDOWN = 60000; // 1分钟冷却时间

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotificationListener Service Created");
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        createNotificationChannel();

        // Register broadcast receiver for test alerts
        testAlertReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TEST_ALERT_ACTION.equals(intent.getAction())) {
                    Log.i(TAG, "Test Alert Received!");
                    triggerAlert("Test Alert", "This is a test notification");
                }
            }
        };

        IntentFilter filter = new IntentFilter(TEST_ALERT_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testAlertReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(testAlertReceiver, filter);
        }
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

        // Show toast for debugging (临时调试用)
        showDebugToast("通知: " + packageName + "\n标题: " + title);

        // Filter for TradingView app or email apps with "TradingView" in content
        if (isTradingViewAlert(packageName, title, text)) {
            // 创建唯一标识符（使用通知key或组合title+text）
            final String notificationKey;
            if (sbn.getKey() != null) {
                notificationKey = sbn.getKey();
            } else {
                notificationKey = packageName + ":" + title + ":" + text;
            }

            // 检查是否已处理过这个通知
            if (processedNotifications.contains(notificationKey)) {
                Log.d(TAG, "Notification already processed, skipping: " + notificationKey);
                return;
            }

            // 标记为已处理
            processedNotifications.add(notificationKey);

            Log.i(TAG, "TradingView Alert Detected!");
            triggerAlert(title, text);

            // 1分钟后清除这个通知的记录，允许相同警报再次触发
            new Thread(() -> {
                try {
                    Thread.sleep(NOTIFICATION_COOLDOWN);
                    processedNotifications.remove(notificationKey);
                    Log.d(TAG, "Notification cooldown expired: " + notificationKey);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            // Log why it wasn't detected
            Log.d(TAG, "Not a TradingView alert - Package: " + packageName);
        }
    }

    private void showDebugToast(final String message) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isTradingViewAlert(String packageName, String title, String text) {
        // TradingView app package names
        if (packageName.contains("tradingview") ||
            packageName.contains("com.tradingview")) {
            return true;
        }

        // Email apps with TradingView in content
        String[] emailApps = {
            "com.google.android.gm",           // Gmail
            "com.microsoft.office.outlook",    // Outlook
            "com.yahoo.mobile.client",         // Yahoo Mail
            "com.samsung.android.email",       // Samsung Email
            "com.tencent.androidqqmail",       // QQ邮箱
            "com.tencent.qqlite",              // QQ轻聊版
            "com.tencent.mobileqq"             // QQ (可能也用于邮件通知)
        };

        for (String emailApp : emailApps) {
            if (packageName.equals(emailApp)) {
                String content = (title + " " + text).toLowerCase();
                // 检测TradingView相关关键词
                if (content.contains("tradingview") ||
                    content.contains("alert") ||
                    content.contains("警报") ||
                    content.contains("提醒") ||
                    content.contains("btc") ||
                    content.contains("eth") ||
                    content.contains("usdt")) {
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

            // Auto stop after 5 minutes
            new Thread(() -> {
                try {
                    Thread.sleep(300000);
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

            // Auto stop vibration after 5 minutes
            new Thread(() -> {
                try {
                    Thread.sleep(300000);
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
        if (testAlertReceiver != null) {
            unregisterReceiver(testAlertReceiver);
        }
        Log.d(TAG, "NotificationListener Service Destroyed");
    }
}
