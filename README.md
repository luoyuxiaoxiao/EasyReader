# EasyReader

EasyReader is an Android EPUB reader MVP focused on a small local library and a calm long-form reading flow.

## Current MVP Scope

- Import EPUB files through Android Storage Access Framework.
- Detect duplicate imports with SHA-256.
- Show imported books in a local bookshelf.
- Open EPUB books in a vertically scrolling reader powered by Readium.
- Switch chapters with horizontal swipes while keeping vertical scrolling smooth.
- Persist and restore reading progress.
- Show whole-book and current-chapter reading percentages.
- Request pinned launcher shortcuts that reopen a selected book.

Not included yet: PDF support, cloud sync, accounts, notes, highlights, search, TTS, OPDS, or page-turn mode.

## Prerequisites

- JDK 21.
- Android SDK with platform 36 installed. The app currently targets SDK 35.
- A connected Android device or emulator for instrumentation tests.
- The checked-in Gradle wrapper 9.2.0. No global Gradle install is required.

## Build

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

## Test

Run JVM unit tests:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --no-daemon
```

Run instrumentation tests with a device or emulator attached:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Install the debug APK on a connected device:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:installDebug --no-daemon
```

## VS Code

Open the repository root in VS Code. The checked-in `.vscode` configuration sets JDK 21, Android SDK paths, Gradle wrapper import, and common tasks.

Recommended extensions:

- Extension Pack for Java, or at least `redhat.java`.
- Gradle for Java: `vscjava.vscode-gradle`.
- Kotlin Language: `fwcd.kotlin`.

Common tasks are available through `Terminal > Run Task`:

- `Gradle: assemble debug`
- `Gradle: unit tests`
- `Gradle: clean`
- `Android: install debug`
- `Android: instrumentation tests`

The Android install and instrumentation tasks require a connected device or a running emulator.

Android Studio is not required to edit or build this project. It is still useful for AVD Manager, Logcat, layout inspection, and SDK package management. If you prefer VS Code only, install the Android Emulator and a system image with `sdkmanager`, then create and start an AVD with `avdmanager` and `emulator`.

## First Run

Start the app and import EPUB files with the in-app import button. Pinned shortcuts require launcher confirmation, so Android will ask before placing each selected book shortcut on the home screen.

## Manual Acceptance

Before treating a build as release-ready, run the device checks with a phone or emulator attached:

- Import one or more EPUB files from the in-app import button.
- Re-import the same EPUB and confirm it is skipped as a duplicate.
- Open a book from the bookshelf and scroll through a long chapter.
- Swipe horizontally to switch chapters without triggering changes during diagonal vertical scroll.
- Exit and reopen the app, then confirm the last reading location is restored.
- Add a pinned shortcut for a selected book and confirm it opens directly into that book.
