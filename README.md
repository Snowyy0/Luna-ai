# Luna-ai

Native Android app built with Kotlin and Jetpack Compose. Open `client` in Android Studio. The Expo/React Native frontend has been removed.

The app focuses on a Luna chat, with an optional evening color theme and gentle reminders.

- Add and remove exam dates in the calendar. Exam reminders arrive at 9 AM each day during the final seven days, including exam day.
- Opt in to exam and chat reminders in Profile. Chat reminders are gentle and sent at most once a day when you have not opened Luna.
- Exam dates are stored on the device. Chat messages are sent individually to the configured Luna server.
- Profile contains your name, reminder preferences, reply language, and the server session token.

## Build and check

From `client`, with Java 17+ and the Android SDK configured:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is written to `client/app/build/outputs/apk/debug/app-debug.apk`.

## Run on an Android phone over USB

1. Create `server/.env` with `OPENROUTER_API_KEY=your_new_key`. Keep it private. Leave the backend on `127.0.0.1:8787`.
2. From `server`, run `npm start` (Node 22+). AI replies require a valid OpenRouter key and credit.
3. Enable USB debugging on the phone and accept its authorization prompt.
4. Run `adb reverse tcp:8787 tcp:8787` using Android SDK platform-tools.
5. Open `client` in Android Studio, select the phone, and run the debug app. Alternatively, run `./gradlew.bat :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk` from `client`.
6. If `LUNA_SESSION_TOKEN` is set in `server/.env`, open Profile → Settings and enter it in the Session token field. This is separate from the OpenRouter API key. The app keeps the token in memory; enter it again after the app process restarts.

The debug app uses `10.0.2.2` to reach the host from an Android emulator, and `127.0.0.1` through USB on a physical phone. Repeat `adb reverse` after reconnecting a phone. Release builds require HTTPS; this local testing app is intended for debug builds. Messages are sent individually without history and remain on screen for retry.
Luna is an AI well-being companion for Tunisian university students. It offers supportive conversations and simple calming activities; it is not a therapist or emergency service.
