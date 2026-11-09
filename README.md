# flash-sale-bug-hunter

## QA Bug Hunter

Android QA overlay for manual exploratory testing. It records tester actions and bug markers, shows session timing, a manual countdown, and local Wi-Fi signal. It does not read or control the app under test and cannot perform transactions.

## Build and install

Prerequisites: JDK 17 and Android SDK Platform 35. Set `ANDROID_HOME` to your SDK directory and ensure the Android SDK Build-Tools are installed. The Gradle Wrapper downloads the pinned Gradle version on first use.

```powershell
.\gradlew.bat assembleDebug
```

Install the debug APK on a connected device with:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Open **QA Bug Hunter**, set a countdown duration, choose event and bug categories, and optionally enter a tester note. Tap **Tampilkan panel** and grant **Display over other apps** and notification permissions when asked.

In the floating panel, **START** creates a session, **GO** records a categorized manual event, **BUG** records a bug marker with a unique reference, and **STOP** closes the session and writes a summary. The session timer continues independently of the countdown. The panel reports Wi-Fi RSSI and link speed, and notifies when Wi-Fi falls below -75 dBm, when about one minute remains, and when the countdown ends.

During a suspected outage, the tester can tap **PAGE DOWN** to log the event and pause the local countdown. Tap **RECOVERED** only after confirming the page is available again; the recovery is logged and the remaining countdown resumes. No request is retried and no action is sent to the app under test.

Each session is appended as JSON Lines in the app-private `files/sessions` directory on the Android device. Records include session/event IDs, UTC wall-clock and monotonic timestamps, elapsed time, categories, notes, bug references, and session-start device/app metadata. This data is stored on the device, not in `C:`. Screenshot references are reserved but evidence capture/export are not implemented yet.

If USB installation is blocked by Xiaomi, approve the install prompt on the phone or enable **Install via USB** in Developer options, then run the `adb install -r` command again.