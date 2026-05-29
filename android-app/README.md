# Income Expense Tracker Android App

This is the mobile version of the Income Expense Tracker. It is a real Android app with its own local SQLite database on the phone.

## Open in Android Studio

1. Open Android Studio.
2. Choose `Open`.
3. Select this folder:

```text
C:\Users\ADMIN\Documents\Expense_Tracker\android-app
```

4. Wait for Gradle sync to finish.
5. Click `Run` to test on an emulator or connected Android phone.

## Build an APK

In Android Studio:

1. Go to `Build`.
2. Choose `Build Bundle(s) / APK(s)`.
3. Choose `Build APK(s)`.
4. After the build, click `locate`.

The APK is usually created here:

```text
android-app\app\build\outputs\apk\debug\app-debug.apk
```

Send that APK to your Android phone, open it, and install it. If Android asks, allow installation from that file source.

## Notes

- No server is needed.
- No CMD is needed.
- Data is saved only on the Android device where the app is installed.
- Uninstalling the app can delete its local saved data.
