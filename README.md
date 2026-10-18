# Luna-ai

Native Android app built with Kotlin and Jetpack Compose. Open `client` in Android Studio. The Expo/React Native frontend has been removed.

The UI follows the supplied pastel kitten templates: welcome, chat with floating mascot, profile, a vertical feed with breathing/slide/drag/mood moments, a settings bottom sheet, connection errors with retry, and an evening color theme. The artwork is a generated interpretation of the reference kitten.

- Tap the center arrow to open moments; swipe up or tap it again to advance.
- Profile contains goals, session statistics, settings, and help. The name starts as Youssef and is editable.
- The microphone opens Android voice dictation for review before sending. The waveform reads Luna's reply aloud; tap again to stop. Both depend on the device's installed speech services.
- Settings includes reduced motion, evening colors, reply language, and the server session token. Goals and check-ins are session-only; “Log out” clears local session data rather than signing out of an online account.

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

The debug app connects to `http://127.0.0.1:8787` through USB. Repeat `adb reverse` after reconnecting. Release builds require HTTPS; this local testing app is intended for debug builds. Messages are sent individually without history and remain on screen for retry.
Luna is an AI well-being companion for Tunisian university students. It offers supportive conversations and simple calming activities; it is not a therapist or emergency service.
