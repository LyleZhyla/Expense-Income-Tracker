# Income Expense Tracker

A local Flask desktop-style app for tracking income, expenses, and running balance. Data is stored in a private SQLite database on the same computer, so no MySQL setup is needed.

## Run while developing

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe app.py
```

The app opens `http://127.0.0.1:5000` automatically.

## Build the downloadable Windows app

```powershell
.\build_app.ps1
```

After building, open:

```powershell
dist\Income Expense Tracker\Income Expense Tracker.exe
```

You can zip the whole `dist\Income Expense Tracker` folder and share it as the downloadable app. Keep the files in that folder together.

The Windows build opens without a CMD window.

## Mobile Android app

The native Android version is in:

```text
android-app
```

Open `android-app` in Android Studio, then use `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

The APK is usually created here:

```text
android-app\app\build\outputs\apk\debug\app-debug.apk
```

Send the APK to your Android phone and install it. This mobile version does not need Flask, MySQL, CMD, or a browser server.

## Data location

During development, the database is saved in `instance\income_expense_tracker.sqlite3`.

In the packaged app, the database is saved in:

```text
%LOCALAPPDATA%\IncomeExpenseTracker\income_expense_tracker.sqlite3
```
