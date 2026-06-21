# EasyReader v0.1.1 Progress Handoff

## Current State

- Main workspace: `/home/luoyu/Projects/AndroidAPP/EasyReader`
- Implementation worktree: `/home/luoyu/Projects/AndroidAPP/EasyReader/.worktrees/bugfix-main`
- Implementation branch: `bugfix/main-bugs`
- Target release branch: `main`
- Target release: `v0.1.1`
- Release script: `EasyReaderAPK.sh --remote 0.1.1`

## Completed Scope

- Reader rotation crash fix:
  - EPUB reading page no longer crashes during Activity recreation caused by screen rotation.
  - Reader startup now prefers the latest persisted locator so orientation changes keep the current reading position as closely as possible.
- Chapter gesture tuning:
  - Left and right edge 32dp zones are reserved for system back gestures.
  - Chapter switching requires horizontal movement to be at least 2x vertical movement.
  - Fast horizontal fling follows the same directional constraint.
  - Vertical reading scroll is less likely to trigger accidental chapter switches.
- Reader chrome behavior:
  - EPUB opens with chrome hidden by default.
  - Tapping the page toggles global chrome visibility.
  - While scrolling, top chrome stays hidden and the bottom progress chrome is shown temporarily.
  - If the finger remains down after scrolling, the bottom progress chrome remains visible so the user can continue adjusting position.
- Font scaling:
  - Pinch zoom adjusts reader font size directly.
  - The current font size appears briefly in orange at screen center while zooming.
  - The font-size overlay has no background, matching the temporary UI direction for this version.
- Release infrastructure:
  - GitHub Actions release workflow can read `docs/releases/easyreader-<version>-release.md` as the GitHub Release body.
  - `EasyReaderAPK.sh` preserves prewritten release notes instead of overwriting them.
  - Script tests cover preserving existing release notes.

## Verification Commands

These commands were run from the implementation worktree before merging and publishing:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Result: `BUILD SUCCESSFUL in 3m 30s`.

```bash
timeout 600s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:connectedDebugAndroidTest --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=io.github.luoyuxiaoxiao.easyreader.ui.reader.ReaderActivityRecreationTest
```

Result: `BUILD SUCCESSFUL in 1m 40s`; 1 test finished on `Android_34_Emulator(AVD) - 14`.

```bash
bash tests/EasyReaderAPK_test.sh
```

Result: `EasyReaderAPK tests passed`.

```bash
bash EasyReaderAPK.sh --check 0.1.1
```

Result: check mode confirmed `versionCode: 1 -> 2`, `versionName: 0.1.0 -> 0.1.1`, and `tag: v0.1.1`.

## Release Steps

1. Commit the implementation branch.
2. Fast-forward merge `bugfix/main-bugs` into `main`.
3. Run `EASYREADERAPK_CONFIRM=yes bash EasyReaderAPK.sh --remote 0.1.1` from the main workspace.
4. Wait for GitHub Actions release build to finish.
5. Confirm `https://github.com/luoyuxiaoxiao/EasyReader/releases/tag/v0.1.1` contains the APK, SHA-256 file, and the release notes from `docs/releases/easyreader-0.1.1-release.md`.

## Next Goals

- Manually test multiple real EPUB files after installing `v0.1.1`.
- Tune exact gesture thresholds if real-device use still feels too strict or too sensitive.
- Unify reader UI color and chrome design in a later version; orange is intentionally temporary for the progress and font-size indicators.
- Add broader instrumentation coverage for long chapter scroll, chapter boundary switching, and persisted progress across app restarts.
