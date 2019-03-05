# The Story

DND synchronization between and Android Wear watch and Android phone was removed in Wear 2.0 version. This application is intended to restore this functionality.

XDA thread: https://forum.xda-developers.com/wear-os/development/app-disturb-sync-wear-2-0-t3603086

# Support
Phone support:
- Android 5+ required
- Android Go not supported
- DND toggle available
- Sound mode (sound/vibrate/silent) sinchronization not supported
  - Some phones like OnePlus (and other Chinese brands) still use Sound modes (deprecated since Android 7)
- Google compliant Battery saver policy
  - Check if your phone maker is blacklisted at https://dontkillmyapp.com/ and what you can do about it

Watch support:
- Android 7+ required
- Wear 2.0+ required
- Notification Service support is removed from Wear H emulator. Some Wear H watches also have this service removed. More watches  are expected to get this change.

# Setup
> ADB setup instructions not provided. If you don't know how to setup ADB, you should not modify your device. 

- Download newest apks from https://github.com/rkkr/wear-dnd-sync/releases
- Connect Phone to ADB
- `adb install mobile-release.apk`
  - To update:  `adb install -r mobile-release.apk`
- Disconnect Phone
- Start Phone app, click `Phone Permission Setup` and grant requested permission
- Connect watch to ADB
- `adb install wear-release.apk`
  - To update:  `adb install -r wear-release.apk`
- `adb shell settings put secure enabled_notification_listeners com.google.android.wearable.app/com.google.android.clockwork.stream.NotificationCollectorService:rkr.weardndsync/rkr.weardndsync.NotificationService`
- Start Phone app, make sure all checks pass
- If checks don't pass:
  - You made an error in your setup
  - Your device is not supported
  - You can try to analyze the logs that can be extracted with `Send Logs` button
  - Make a PR with fixes
