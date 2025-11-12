package com.tradingview.alertapp;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AlertManager {
    private static final String TAG = "AlertManager";
    private static final String STOP_ALARM_ACTION = "com.tradingview.alertapp.STOP_ALARM";

    // 报警配置
    private static final long ALERT_DURATION = 180000; // 3分钟
    private static final long REPEAT_INTERVAL = 600000; // 10分钟
    private static final int MAX_REPEATS = 6; // 最多6次

    private final Context context;
    private final Vibrator vibrator;
    private MediaPlayer mediaPlayer;
    private final Handler handler;
    private final NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    // 跟踪每个警报的重复次数
    private final Map<String, AlertInfo> activeAlerts = new HashMap<>();
    private final AtomicInteger notificationIdCounter = new AtomicInteger(1000);

    private BroadcastReceiver stopAlarmReceiver;

    private static class AlertInfo {
        String title;
        String message;
        int repeatCount;
        int notificationId;
        Runnable repeatTask;

        AlertInfo(String title, String message, int notificationId) {
            this.title = title;
            this.message = message;
            this.repeatCount = 0;
            this.notificationId = notificationId;
        }
    }

    public AlertManager(Context context) {
        this.context = context;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        setupStopAlarmReceiver();
    }

    private void setupStopAlarmReceiver() {
        stopAlarmReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (STOP_ALARM_ACTION.equals(intent.getAction())) {
                    String alertKey = intent.getStringExtra("alertKey");
                    Log.i(TAG, "Stop alarm requested for: " + alertKey);
                    stopAlert(alertKey);
                }
            }
        };

        IntentFilter filter = new IntentFilter(STOP_ALARM_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stopAlarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(stopAlarmReceiver, filter);
        }
    }

    public void triggerAlert(String title, String message) {
        String alertKey = title + ":" + message;

        // 如果这个警报已经在活跃中，忽略
        if (activeAlerts.containsKey(alertKey)) {
            Log.d(TAG, "Alert already active, ignoring: " + alertKey);
            return;
        }

        int notificationId = notificationIdCounter.incrementAndGet();
        AlertInfo alertInfo = new AlertInfo(title, message, notificationId);
        activeAlerts.put(alertKey, alertInfo);

        // 开始第一次报警
        performAlert(alertKey, alertInfo);
    }

    private void performAlert(String alertKey, AlertInfo alertInfo) {
        Log.i(TAG, "Performing alert " + (alertInfo.repeatCount + 1) + "/" + MAX_REPEATS + ": " + alertKey);

        // 获取 WakeLock 防止设备休眠影响振动和声音
        acquireWakeLock();

        // 播放声音和震动
        playAlarmSound();
        vibratePhone();

        // 显示通知（带停止按钮）
        showAlertNotification(alertKey, alertInfo);

        // 3分钟后停止声音和震动
        handler.postDelayed(() -> {
            stopSoundAndVibration();
        }, ALERT_DURATION);

        // 增加重复次数
        alertInfo.repeatCount++;

        // 如果还没到最大次数，安排下次报警
        if (alertInfo.repeatCount < MAX_REPEATS) {
            Runnable repeatTask = () -> {
                if (activeAlerts.containsKey(alertKey)) {
                    performAlert(alertKey, alertInfo);
                }
            };
            alertInfo.repeatTask = repeatTask;
            handler.postDelayed(repeatTask, REPEAT_INTERVAL);
            Log.d(TAG, "Scheduled next alert in 10 minutes");
        } else {
            // 到达最大次数，自动清理
            Log.i(TAG, "Reached maximum repeats, stopping alert: " + alertKey);
            activeAlerts.remove(alertKey);
        }
    }

    private void stopAlert(String alertKey) {
        AlertInfo alertInfo = activeAlerts.get(alertKey);
        if (alertInfo != null) {
            // 取消下次重复
            if (alertInfo.repeatTask != null) {
                handler.removeCallbacks(alertInfo.repeatTask);
            }

            // 移除通知
            notificationManager.cancel(alertInfo.notificationId);

            // 从活跃列表移除
            activeAlerts.remove(alertKey);

            // 停止声音和震动
            stopSoundAndVibration();

            Log.i(TAG, "Alert stopped manually: " + alertKey);
        }
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
            mediaPlayer.setDataSource(context, alarmUri);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
            mediaPlayer.setAudioAttributes(audioAttributes);

            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            Log.d(TAG, "Alarm sound started");
        } catch (IOException e) {
            Log.e(TAG, "Error playing alarm sound", e);
        }
    }

    private void vibratePhone() {
        if (vibrator != null && vibrator.hasVibrator()) {
            // 使用足够长的振动pattern来覆盖3分钟
            // 每个周期：振1秒，停0.5秒 = 1.5秒
            // 3分钟 = 180秒 = 120个周期
            // 使用-1表示不重复，直接播放完整个pattern
            long[] pattern = new long[241]; // 0 + 120*2 = 241个元素
            pattern[0] = 0; // 初始延迟
            for (int i = 1; i < 241; i += 2) {
                pattern[i] = 1000;     // 振动1秒
                if (i + 1 < 241) {
                    pattern[i + 1] = 500;  // 停0.5秒
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, -1);
            }
            Log.d(TAG, "Vibration started (3-minute pattern, no repeat)");
        }
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null || !wakeLock.isHeld()) {
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (powerManager != null) {
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "TVAlert:AlertWakeLock"
                    );
                    wakeLock.acquire(ALERT_DURATION + 5000); // 3分钟 + 5秒缓冲
                    Log.d(TAG, "WakeLock acquired");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring WakeLock", e);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock released");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing WakeLock", e);
        }
    }

    private void stopSoundAndVibration() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            Log.d(TAG, "Alarm sound stopped");
        }

        if (vibrator != null) {
            vibrator.cancel();
            Log.d(TAG, "Vibration stopped");
        }

        releaseWakeLock();
    }

    private void showAlertNotification(String alertKey, AlertInfo alertInfo) {
        // 创建停止按钮的Intent
        Intent stopIntent = new Intent(STOP_ALARM_ACTION);
        stopIntent.putExtra("alertKey", alertKey);
        stopIntent.setPackage(context.getPackageName());

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
            context,
            alertInfo.notificationId,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 创建点击通知的Intent（点击也能停止）
        PendingIntent contentIntent = PendingIntent.getBroadcast(
            context,
            alertInfo.notificationId + 10000,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String contentText = String.format("Alert %d/%d: %s - %s\n\n👆 点击通知或按下方\"停止\"按钮关闭警报",
            alertInfo.repeatCount, MAX_REPEATS, alertInfo.title, alertInfo.message);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "tv_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🔔 TradingView Alert!")
            .setContentText(contentText)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(contentIntent)  // 点击通知也能停止
            .addAction(android.R.drawable.ic_delete, "停止", stopPendingIntent);

        notificationManager.notify(alertInfo.notificationId, builder.build());
        Log.d(TAG, "Alert notification shown with Stop button");
    }

    public void cleanup() {
        if (stopAlarmReceiver != null) {
            context.unregisterReceiver(stopAlarmReceiver);
        }
        stopSoundAndVibration();
        handler.removeCallbacksAndMessages(null);
        activeAlerts.clear();
    }
}
