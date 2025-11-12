# TradingView Alert Monitor

Native Android app that monitors TradingView notifications in real-time with **zero latency** and triggers loud alerts.

## Features

- **Zero Latency**: Monitors notifications directly from TradingView app or email apps
- **No Server Required**: Works completely offline using Android NotificationListenerService
- **Loud Alerts**: Plays alarm sound at maximum volume with continuous vibration
- **Auto-Detection**: Automatically detects TradingView alerts from:
  - TradingView app notifications
  - Email notifications containing "TradingView" or "Alert"

## How It Works

1. Install the app
2. Enable notification access in Android settings
3. The app runs in the background monitoring all notifications
4. When TradingView sends an alert (via app or email), this app instantly:
   - Plays loud alarm sound (30 seconds)
   - Vibrates continuously (30 seconds)
   - Shows a high-priority notification

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

## Advantages Over Server-Based Approach

| Feature | This App | Email Watcher Server |
|---------|----------|---------------------|
| Latency | 0s (instant) | ~43s average |
| Server Required | No | Yes |
| Network Required | No | Yes |
| Power Consumption | Very Low | Medium |
| Reliability | Very High | Depends on network |

## Permissions

- **Notification Access**: Required to monitor notifications
- **Vibrate**: For vibration alerts
- **Wake Lock**: Keep device awake during alerts
- **Foreground Service**: Run in background

## License

MIT License
