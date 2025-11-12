# TradingView Alert Monitor

Native Android app that monitors TradingView notifications in real-time with **zero latency** and triggers loud, persistent alerts.

## Features

- **Zero Latency**: Monitors notifications directly from TradingView app or email apps
- **No Server Required**: Works completely offline using Android NotificationListenerService
- **Persistent Alerts**:
  - 3-minute alarm sound at maximum volume
  - Continuous vibration for 3 minutes
  - Auto-repeat every 10 minutes (up to 6 times total)
- **Manual Control**: Stop button on notification to cancel alerts
- **Anti-Duplicate**: Intelligent deduplication prevents multiple alerts from same trigger
- **Auto-Detection**: Automatically detects TradingView alerts from:
  - TradingView app notifications
  - Email notifications (Gmail, Outlook, QQ Mail, etc.)
  - AlertPollingService (network-based backup)

## How It Works

1. Install the app
2. Enable notification access in Android settings
3. The app runs in the background monitoring all notifications
4. When TradingView sends an alert (via app or email), this app instantly:
   - Plays loud alarm sound for **3 minutes**
   - Vibrates continuously for **3 minutes**
   - Shows high-priority notification with **stop button**
   - If not manually stopped, repeats **every 10 minutes** (max **6 times**)

## Installation

### Download APK

Download the latest APK from [GitHub Actions artifacts](../../actions) or [Releases](../../releases).

### Enable Notification Access

1. Open the app
2. Tap "Enable Notification Access"
3. Find "TV Alert Monitor" in the list
4. Toggle ON
5. Confirm permissions

## Tested Email Apps

- Gmail
- Outlook
- Yahoo Mail
- Samsung Email

## Technical Details

- **Minimum Android Version**: 5.0 (API 21)
- **Target Android Version**: 13 (API 33)
- **Language**: Java
- **Architecture**: arm64-v8a

## Building from Source

```bash
./gradlew assembleRelease
```

## Alert Behavior

- **Duration**: 3 minutes of continuous sound + vibration
- **Repeat**: If not manually stopped, repeats every 10 minutes
- **Max Repeats**: 6 times total (covering 1 hour)
- **Manual Stop**: Tap notification or "停止" button to cancel all pending repeats
- **Anti-Duplicate**: 60-second cooldown prevents duplicate alerts from same trigger

## Dual Backup System

This app supports **three alert trigger methods** for maximum reliability:

### 🥇 Method A: TradingView App Notifications (Primary)
- **Latency**: 1-3 seconds
- **Offline**: ✅ Works without internet
- **Reliability**: ⭐⭐⭐⭐⭐
- **Setup**: Install TradingView app, enable notifications

### 🥈 Method B: Email Monitoring via Computer (Backup)
- **Latency**: 10-30 seconds
- **Requires**: PC running email_watcher.py
- **Reliability**: ⭐⭐⭐⭐
- **Setup**: See DUAL_BACKUP_SETUP.md

### 🥉 Method C: Email App Notifications (Dual Backup)
- **Latency**: 10-30 seconds
- **Requires**: Phone online + email app installed
- **Reliability**: ⭐⭐⭐⭐
- **Setup**: Install Gmail/QQ Mail app, enable notifications

## Advantages Over Server-Based Approach

| Feature | This App | Email Watcher Server |
|---------|----------|---------------------|
| Latency | 1-3s (instant) | 10-30s average |
| Server Required | No | Yes |
| Network Required | No (Method A) | Yes |
| Offline Support | ✅ Yes | ❌ No |
| Power Consumption | Very Low | Medium |
| Reliability | Very High | Depends on network |
| Alert Duration | 3 minutes | Configurable |
| Auto-Repeat | 6x every 10min | No |

## Permissions

- **Notification Access**: Required to monitor notifications
- **Vibrate**: For vibration alerts
- **Wake Lock**: Keep device awake during alerts
- **Foreground Service**: Run in background

## License

MIT License
