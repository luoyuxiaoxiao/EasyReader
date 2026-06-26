# Reader Gesture Readest Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the reader touch pipeline around a readest-style priority interceptor chain while preserving current tap, scroll, pinch, and chapter navigation behavior.

**Architecture:** `ReaderGestureLayout` stays the only Android `MotionEvent` entry point. Gesture sampling, priority ownership, and concrete consumers move into focused classes under `reader/gesture`, with `ReaderGestureLayout` responsible for forwarding child events and sending exactly one child `ACTION_CANCEL` when an interceptor consumes the gesture.

**Tech Stack:** Kotlin, Android View dispatch, Robolectric/JUnit unit tests, Readium WebView child dispatch.

---

## File Structure

- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptor.kt`
  - Extend `TouchDetail` with sampled gesture fields and add `TouchDisposition` so interceptors can declare whether child events must be cancelled.
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptorRegistry.kt`
  - Add current-owner lifecycle and route later MOVE/UP/CANCEL events to the owner.
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchGestureSampler.kt`
  - Convert `MotionEvent` streams into stable `TouchDetail`, including historical points.
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/GestureThresholds.kt`
  - Add chapter fast/slow thresholds and keep existing tap/scroll/chrome values centralized.
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeInterceptor.kt`
  - Implement arm/commit chapter swipe with child-cancel intent.
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/VerticalScrollInterceptor.kt`
  - Own vertical scroll start/finish and suppress-window timing.
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`
  - Replace local path/lock state with sampler + registry orchestration.
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptorRegistryTest.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchGestureSamplerTest.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeInterceptorTest.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/VerticalScrollInterceptorTest.kt`
- Modify: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayoutTest.kt`

## Task 1: Registry Ownership

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptor.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptorRegistry.kt`
- Test: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptorRegistryTest.kt`

- [ ] Write failing tests for priority order, owner lock, and cleanup.
- [ ] Run `timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.reader.gesture.TouchInterceptorRegistryTest`.
- [ ] Implement `TouchDisposition` and owner routing.
- [ ] Re-run the focused registry test.

## Task 2: Gesture Sampler

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchGestureSampler.kt`
- Test: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchGestureSamplerTest.kt`

- [ ] Write failing tests proving historical points update `pathAbsDx/pathAbsDy`, net/peak values, duration, velocity, tap candidates, and system-edge detection.
- [ ] Run focused sampler tests and verify failure.
- [ ] Implement `TouchGestureSampler`.
- [ ] Re-run focused sampler tests.

## Task 3: Vertical Scroll Consumer

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/VerticalScrollInterceptor.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/TouchInterceptor.kt`
- Test: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/VerticalScrollInterceptorTest.kt`

- [ ] Write failing tests for `12dp` + `1.2x` vertical lock, start callback once, finish callback once, and `450ms` suppression window.
- [ ] Run focused vertical tests and verify failure.
- [ ] Implement `VerticalScrollInterceptor`.
- [ ] Re-run focused vertical tests.

## Task 4: Chapter Swipe Consumer

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/GestureThresholds.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeInterceptor.kt`
- Test: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeInterceptorTest.kt`

- [ ] Write failing tests for slow horizontal swipe, fast short swipe, vertical-dominant rejection, system-edge rejection, cooldown rejection, arm-time child cancel, and ambiguous horizontal tail consumption without chapter commit.
- [ ] Run focused chapter tests and verify failure.
- [ ] Implement arm/commit thresholds with slow and fast lanes.
- [ ] Re-run focused chapter tests.

## Task 5: ReaderGestureLayout Integration

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`
- Modify: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayoutTest.kt`

- [ ] Write failing layout tests proving chapter arm sends one child `ACTION_CANCEL`, UP is swallowed after consumption, vertical scroll still reaches child, and tap still uses content-first chrome logic.
- [ ] Run focused layout tests and verify failure.
- [ ] Integrate sampler, registry, vertical interceptor, and chapter interceptor into `ReaderGestureLayout`.
- [ ] Re-run focused layout tests.

## Task 6: Full Verification

**Files:**
- No new source files.

- [ ] Run `timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Review `git diff --stat` and summarize remaining risk.
