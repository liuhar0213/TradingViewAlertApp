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
    private AlertManager alertManager;
    private BroadcastReceiver testAlertReceiver;

    // 防止重复报警：记录已处理的通知
    private final java.util.Set<String> processedNotifications = new java.util.HashSet<>();
    private static final long NOTIFICATION_COOLDOWN = 60000; // 1分钟冷却时间

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NotificationListener Service Created");
        alertManager = new AlertManager(this);
        createNotificationChannel();

        // Register broadcast receiver for test alerts
        testAlertReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TEST_ALERT_ACTION.equals(intent.getAction())) {
                    Log.i(TAG, "Test Alert Received!");
                    alertManager.triggerAlert("Test Alert", "This is a test notification");
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
            alertManager.triggerAlert(title, text);

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
        if (alertManager != null) {
            alertManager.cleanup();
        }
        if (testAlertReceiver != null) {
            unregisterReceiver(testAlertReceiver);
        }
        Log.d(TAG, "NotificationListener Service Destroyed");
    }
}
