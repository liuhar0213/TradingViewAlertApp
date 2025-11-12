package com.tradingview.alertapp;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView statusText;
    private Button enableButton;
    private Button testButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        enableButton = findViewById(R.id.enableButton);
        testButton = findViewById(R.id.testButton);

        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotificationSettings();
            }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTestDialog();
            }
        });

        // Start polling service automatically
        startAlertPollingService();
    }

    private void startAlertPollingService() {
        Intent intent = new Intent(this, AlertPollingService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (isNotificationServiceEnabled()) {
            statusText.setText("✓ Notification Listener is ENABLED\n\n" +
                "The app is now monitoring:\n" +
                "• TradingView app notifications\n" +
                "• Email notifications containing 'TradingView' or 'Alert'\n\n" +
                "When detected, it will:\n" +
                "• Play loud alarm sound\n" +
                "• Vibrate continuously\n" +
                "• Show alert notification");
            statusText.setTextColor(0xFF4CAF50); // Green
            enableButton.setEnabled(false);
            enableButton.setText("Notification Access Granted");
            testButton.setEnabled(true);
        } else {
            statusText.setText("✗ Notification Listener is DISABLED\n\n" +
                "Please enable notification access for this app to monitor TradingView alerts.");
            statusText.setTextColor(0xFFF44336); // Red
            enableButton.setEnabled(true);
            enableButton.setText("Enable Notification Access");
            testButton.setEnabled(false);
        }
    }

    private boolean isNotificationServiceEnabled() {
        ComponentName cn = new ComponentName(this, NotificationListener.class);
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }

    private void showTestDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Test Alert")
            .setMessage("This will test the alert system.\n\n" +
                "• Alarm duration: 3 minutes\n" +
                "• Repeats: 6 times (every 10 minutes)\n" +
                "• You can stop it manually\n\n" +
                "Continue?")
            .setPositiveButton("Test Alert", (dialog, which) -> {
                // Send broadcast to trigger test alert
                Intent intent = new Intent("com.tradingview.alertapp.TEST_ALERT");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);

                new AlertDialog.Builder(this)
                    .setTitle("Test Alert Triggered")
                    .setMessage("You should now hear an alarm and feel vibration for 3 minutes.\n\n" +
                        "It will repeat 6 times every 10 minutes.\n" +
                        "Tap 'Stop' in the notification to cancel.\n\n" +
                        "If you don't hear anything:\n" +
                        "• Check that notification access is enabled\n" +
                        "• Check your alarm volume settings\n" +
                        "• Make sure 'Do Not Disturb' is off")
                    .setPositiveButton("OK", null)
                    .show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
