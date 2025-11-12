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
            // 长震动pattern：震1秒，停0.5秒，循环
            // pattern[0]=0 是初始延迟，pattern[1]=1000 是振动1秒，pattern[2]=500 是停0.5秒
            // repeat=1 表示从pattern[1]开始无限循环（振动1秒->停0.5秒->振动1秒->停0.5秒...）
            long[] pattern = {0, 1000, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, 1);
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(pattern, 1);
            }
            Log.d(TAG, "Vibration started (infinite loop from index 1)");
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

        String contentText = String.format("Alert %d/%d: %s - %s",
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
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent);

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
