# EasyReader Android EPUB Reader MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first Android MVP for EasyReader: import EPUB files, show a local bookshelf, open a book in smooth vertical scrolling mode, switch chapters with horizontal swipes, persist progress, and create pinned shortcuts that reopen a book at the last location.

**Architecture:** Use one Android `app` module and keep boundaries by Kotlin package instead of early Gradle module splitting. Compose owns native app UI, Readium Kotlin Toolkit owns EPUB parsing and WebView rendering, Room owns local book/progress state, and DataStore owns reader preferences. The reader screen keeps one `Publication` and one `EpubNavigator` session alive while switching chapters through navigator navigation, not by rebuilding the Activity or WebView.

**Tech Stack:** Kotlin 2.3.20, Android Gradle Plugin 9.0.0, Jetpack Compose 1.10.5, Material3 1.4.0, Readium Kotlin Toolkit 3.3.0, Room 2.8.4, DataStore 1.2.1, Coroutines 1.10.2, AndroidX ShortcutManagerCompat.

---

## Scope Boundaries

Included in this MVP:

- Android project scaffold under a single `app` module.
- EPUB-only import through Android Storage Access Framework.
- SHA-256 duplicate detection.
- Local bookshelf list with batch selection.
- Reader page with Readium EPUB rendering in scrolled mode.
- Vertical chapter scrolling, horizontal chapter switching from any reading position.
- Tap-to-toggle reader chrome; hide chrome during vertical scrolling.
- Bottom dual progress display: whole-book percentage and current-chapter percentage.
- Progress persistence with Readium `Locator` JSON.
- Pinned shortcut creation and deep-link handling for selected books.
- Focused unit tests and a small instrumentation test harness.

Excluded from this MVP:

- PDF and non-EPUB formats.
- Cloud sync, accounts, OPDS, online sources, AI, TTS, notes, highlights, full-text search.
- Page-turn mode.
- Custom EPUB layout engine.
- Silent bulk shortcut placement, because Android launchers require user confirmation.

## File Structure

Create this structure during the tasks below:

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/styles.xml
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/EasyReaderApp.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/result/EasyReaderResult.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/book/BookModels.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/book/ReadingProgressFormatter.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/Sha256Hasher.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabase.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookEntities.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookDao.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/ReadingProgressDao.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/ShortcutDao.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/ReaderSettingsStore.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/EpubImportService.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/readium/ReadiumServices.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/readium/EpubReaderSession.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivity.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderChrome.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderViewModel.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/shortcut/ShortcutContract.kt
app/src/main/java/io/github/luoyuxiaoxiao/easyreader/shortcut/ShortcutInstaller.kt
app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/book/ReadingProgressFormatterTest.kt
app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/Sha256HasherTest.kt
app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetectorTest.kt
app/src/test/java/io/github/luoyuxiaoxiao/easyreader/shortcut/ShortcutContractTest.kt
app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt
app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/importer/EpubImportServiceTest.kt
app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/fixtures/MinimalEpubFixture.kt
```

Use `compileSdk = 35` because this machine currently has `/home/luoyu/Android/Sdk/platforms/android-35`.

All core flow comments in production Kotlin files must be concise Simplified Chinese comments, especially the Readium session lifecycle, progress persistence throttling, and chapter swipe state machine.

## Common Commands

Use these commands after Gradle wrapper creation:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --no-daemon
```

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:connectedDebugAndroidTest --no-daemon
```

For local background runs, wrap long test commands with a 60 second timeout:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --no-daemon
```

---

### Task 1: Android Project Scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/EasyReaderApp.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt`

- [ ] **Step 1: Generate Gradle wrapper**

Run:

```bash
gradle wrapper --gradle-version 9.0.0
```

Expected: wrapper files are created under `gradle/wrapper`, plus `gradlew` and `gradlew.bat`.

- [ ] **Step 2: Create Gradle version catalog**

Create `gradle/libs.versions.toml` with fixed MVP versions:

```toml
[versions]
agp = "9.0.0"
kotlin = "2.3.20"
ksp = "2.3.4"
core = "1.18.0"
activity = "1.13.0"
fragment = "1.8.9"
lifecycle = "2.10.0"
compose = "1.10.5"
material3 = "1.4.0"
room = "2.8.4"
datastore = "1.2.1"
coroutines = "1.10.2"
serialization = "1.10.0"
webkit = "1.15.0"
readium = "3.3.0"
desugar = "2.1.5"
junit = "4.13.2"
robolectric = "4.16.1"
androidxTestExt = "1.2.1"
espresso = "3.6.1"

[libraries]
androidx-core = { group = "androidx.core", name = "core-ktx", version.ref = "core" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity" }
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragment" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui", version.ref = "compose" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling", version.ref = "compose" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation", version.ref = "compose" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-webkit = { group = "androidx.webkit", name = "webkit", version.ref = "webkit" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
readium-shared = { group = "org.readium.kotlin-toolkit", name = "readium-shared", version.ref = "readium" }
readium-streamer = { group = "org.readium.kotlin-toolkit", name = "readium-streamer", version.ref = "readium" }
readium-navigator = { group = "org.readium.kotlin-toolkit", name = "readium-navigator", version.ref = "readium" }
desugar-jdk-libs = { group = "com.android.tools", name = "desugar_jdk_libs", version.ref = "desugar" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExt" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: Create root Gradle files**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EasyReader"
include(":app")
```

Create `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 4: Create app module Gradle file**

Create `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.luoyuxiaoxiao.easyreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.luoyuxiaoxiao.easyreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        jvmToolchain(21)
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
```

- [ ] **Step 5: Create manifest and application entry**

Create `app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="com.android.launcher.permission.INSTALL_SHORTCUT" />

    <application
        android:name=".EasyReaderApp"
        android:allowBackup="true"
        android:label="EasyReader"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:host="book"
                    android:scheme="easyreader" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Create `app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="AppTheme" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowActionBar">false</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:fontFamily">sans</item>
        <item name="android:colorAccent">#3F6B57</item>
    </style>
</resources>
```

Create `EasyReaderApp.kt`, `AppContainer.kt`, and `MainActivity.kt` as a minimal boot target:

```kotlin
package io.github.luoyuxiaoxiao.easyreader

import android.app.Application
import io.github.luoyuxiaoxiao.easyreader.core.di.AppContainer

class EasyReaderApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
```

```kotlin
package io.github.luoyuxiaoxiao.easyreader.core.di

import android.content.Context

class AppContainer(val context: Context)
```

```kotlin
package io.github.luoyuxiaoxiao.easyreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("EasyReader")
            }
        }
    }
}
```

- [ ] **Step 6: Run build**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit scaffold**

```bash
git add settings.gradle.kts build.gradle.kts gradle app
git commit -m "chore: scaffold Android app"
```

---

### Task 2: Domain Models, Progress Formatting, and Shortcut Contract

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/result/EasyReaderResult.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/book/BookModels.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/book/ReadingProgressFormatter.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/shortcut/ShortcutContract.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/book/ReadingProgressFormatterTest.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/shortcut/ShortcutContractTest.kt`

- [ ] **Step 1: Write failing progress formatter tests**

Create `ReadingProgressFormatterTest.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.book

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingProgressFormatterTest {
    @Test
    fun formatsNullAsZeroPercent() {
        assertEquals("0.00%", ReadingProgressFormatter.percent(null))
    }

    @Test
    fun clampsValuesOutsideProgressionRange() {
        assertEquals("0.00%", ReadingProgressFormatter.percent(-0.2))
        assertEquals("100.00%", ReadingProgressFormatter.percent(1.2))
    }

    @Test
    fun formatsProgressionWithTwoDecimals() {
        assertEquals("24.96%", ReadingProgressFormatter.percent(0.24956))
        assertEquals("0.40%", ReadingProgressFormatter.percent(0.004))
    }
}
```

- [ ] **Step 2: Write failing shortcut contract tests**

Create `ShortcutContractTest.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.shortcut

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShortcutContractTest {
    @Test
    fun buildsStableBookDeepLink() {
        assertEquals(
            Uri.parse("easyreader://book/book-123"),
            ShortcutContract.bookUri("book-123")
        )
    }

    @Test
    fun parsesBookDeepLink() {
        assertEquals("book-123", ShortcutContract.bookIdFromUri(Uri.parse("easyreader://book/book-123")))
    }

    @Test
    fun rejectsUnknownDeepLink() {
        assertNull(ShortcutContract.bookIdFromUri(Uri.parse("easyreader://settings/book-123")))
    }
}
```

- [ ] **Step 3: Run failing tests**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests "io.github.luoyuxiaoxiao.easyreader.domain.book.*" --tests "io.github.luoyuxiaoxiao.easyreader.shortcut.*" --no-daemon
```

Expected: tests fail because production classes do not exist.

- [ ] **Step 4: Implement domain models and formatters**

Create `EasyReaderResult.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.core.result

sealed interface EasyReaderResult<out T> {
    data class Success<T>(val value: T) : EasyReaderResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : EasyReaderResult<Nothing>
}
```

Create `BookModels.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.book

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val sha256: String,
    val coverPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
)

data class Chapter(
    val id: String,
    val bookId: String,
    val index: Int,
    val href: String,
    val title: String,
    val linear: Boolean,
)

data class ReadingProgress(
    val bookId: String,
    val locatorJson: String,
    val readingOrderIndex: Int,
    val totalProgression: Double?,
    val chapterProgression: Double?,
    val updatedAt: Long,
)

data class ReaderProgressPercentages(
    val total: String,
    val chapter: String,
)
```

Create `ReadingProgressFormatter.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.book

import java.util.Locale

object ReadingProgressFormatter {
    fun percent(progression: Double?): String {
        val normalized = (progression ?: 0.0).coerceIn(0.0, 1.0)
        return String.format(Locale.US, "%.2f%%", normalized * 100.0)
    }
}
```

Create `ShortcutContract.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.shortcut

import android.net.Uri

object ShortcutContract {
    private const val SCHEME = "easyreader"
    private const val HOST_BOOK = "book"

    fun bookUri(bookId: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST_BOOK)
            .appendPath(bookId)
            .build()

    fun bookIdFromUri(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != SCHEME || uri.host != HOST_BOOK) return null
        return uri.pathSegments.singleOrNull()
    }

    fun shortcutId(bookId: String): String = "book-$bookId"
}
```

- [ ] **Step 5: Run tests**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests "io.github.luoyuxiaoxiao.easyreader.domain.book.*" --tests "io.github.luoyuxiaoxiao.easyreader.shortcut.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit domain contract**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain app/src/main/java/io/github/luoyuxiaoxiao/easyreader/shortcut app/src/test
git commit -m "feat: add reader domain contracts"
```

---

### Task 3: Gesture State Machine

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetectorTest.kt`

- [ ] **Step 1: Write failing gesture tests**

Create `ChapterSwipeDetectorTest.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterSwipeDetectorTest {
    private val detector = ChapterSwipeDetector(screenWidthPx = 1080f, density = 3f)

    @Test
    fun leftSwipeSwitchesToNextChapter() {
        val event = detector.evaluate(dxPx = -360f, dyPx = 40f, velocityXPxPerSecond = -1200f)
        assertEquals(ChapterSwipeDecision.NextChapter, event)
    }

    @Test
    fun rightSwipeSwitchesToPreviousChapter() {
        val event = detector.evaluate(dxPx = 360f, dyPx = 40f, velocityXPxPerSecond = 1200f)
        assertEquals(ChapterSwipeDecision.PreviousChapter, event)
    }

    @Test
    fun verticalScrollNeverSwitchesChapter() {
        val event = detector.evaluate(dxPx = 160f, dyPx = 420f, velocityXPxPerSecond = 900f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }

    @Test
    fun shortSlowHorizontalMovementDoesNotSwitchChapter() {
        val event = detector.evaluate(dxPx = -120f, dyPx = 20f, velocityXPxPerSecond = -240f)
        assertEquals(ChapterSwipeDecision.KeepReading, event)
    }
}
```

- [ ] **Step 2: Run failing gesture tests**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests "io.github.luoyuxiaoxiao.easyreader.reader.gesture.*" --no-daemon
```

Expected: tests fail because `ChapterSwipeDetector` does not exist.

- [ ] **Step 3: Implement pure gesture detector**

Create `ChapterSwipeDetector.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.reader.gesture

import kotlin.math.abs
import kotlin.math.max

enum class ChapterSwipeDecision {
    KeepReading,
    PreviousChapter,
    NextChapter,
}

class ChapterSwipeDetector(
    screenWidthPx: Float,
    density: Float,
) {
    private val minHorizontalDistancePx = max(72f * density, screenWidthPx * 0.24f)
    private val fastDistancePx = 48f * density
    private val fastVelocityPxPerSecond = 800f * density
    private val directionRatio = 1.8f

    fun evaluate(
        dxPx: Float,
        dyPx: Float,
        velocityXPxPerSecond: Float,
    ): ChapterSwipeDecision {
        val horizontal = abs(dxPx)
        val vertical = abs(dyPx)

        // 先锁定纵向滚动，避免普通阅读滚动被误判为切章。
        if (vertical > 0f && horizontal < vertical * directionRatio) {
            return ChapterSwipeDecision.KeepReading
        }

        val distanceTriggered = horizontal >= minHorizontalDistancePx
        val flingTriggered =
            horizontal >= fastDistancePx && abs(velocityXPxPerSecond) >= fastVelocityPxPerSecond

        if (!distanceTriggered && !flingTriggered) {
            return ChapterSwipeDecision.KeepReading
        }

        return if (dxPx < 0f) {
            ChapterSwipeDecision.NextChapter
        } else {
            ChapterSwipeDecision.PreviousChapter
        }
    }
}
```

- [ ] **Step 4: Run gesture tests**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests "io.github.luoyuxiaoxiao.easyreader.reader.gesture.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit gesture state machine**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture
git commit -m "feat: add chapter swipe detector"
```

---

### Task 4: Room Database and Repository

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabase.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookEntities.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookDao.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/ReadingProgressDao.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/ShortcutDao.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt`
- Create: `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt`

- [ ] **Step 1: Write instrumentation test for database round trip**

Create `AppDatabaseTest.kt` with an in-memory Room database. Assert that inserting a book, chapters, progress, and shortcut records can be read back by `bookId`, and that `BookDao.findBySha256()` returns the imported book.

- [ ] **Step 2: Run database test and confirm failure**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Expected: database test fails because Room classes do not exist.

- [ ] **Step 3: Implement entities and DAOs**

Create entities matching the spec exactly:

```kotlin
@Entity(tableName = "books", indices = [Index(value = ["sha256"], unique = true)])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val sha256: String,
    val coverPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
)

@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "index"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    val id: String,
    val bookId: String,
    val index: Int,
    val href: String,
    val title: String,
    val linear: Boolean,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ReadingProgressEntity(
    @PrimaryKey val bookId: String,
    val locatorJson: String,
    val readingOrderIndex: Int,
    val totalProgression: Double?,
    val chapterProgression: Double?,
    val updatedAt: Long,
)

@Entity(
    tableName = "shortcuts",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ShortcutEntity(
    @PrimaryKey val bookId: String,
    val shortcutId: String,
    val createdAt: Long,
    val lastRequestedAt: Long,
)
```

DAO requirements:

- `BookDao.observeBooks(): Flow<List<BookEntity>>`
- `BookDao.findById(bookId: String): BookEntity?`
- `BookDao.findBySha256(sha256: String): BookEntity?`
- `BookDao.upsert(book: BookEntity)`
- `ChapterDao.replaceChapters(bookId: String, chapters: List<ChapterEntity>)` using a transaction that deletes old chapters for the book then inserts new chapters.
- `ReadingProgressDao.find(bookId: String): ReadingProgressEntity?`
- `ReadingProgressDao.upsert(progress: ReadingProgressEntity)`
- `ShortcutDao.upsert(shortcut: ShortcutEntity)`

- [ ] **Step 4: Implement database**

Create `AppDatabase.kt`:

```kotlin
@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
        ShortcutEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun shortcutDao(): ShortcutDao
}
```

- [ ] **Step 5: Implement repository mappers**

Create `BookRepository.kt` with methods:

- `observeBooks(): Flow<List<Book>>`
- `findBook(bookId: String): Book?`
- `findDuplicate(sha256: String): Book?`
- `saveImportedBook(book: Book, chapters: List<Chapter>)`
- `progress(bookId: String): ReadingProgress?`
- `saveProgress(progress: ReadingProgress)`
- `recordShortcut(bookId: String, shortcutId: String, now: Long)`

- [ ] **Step 6: Run database verification**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Expected: `BUILD SUCCESSFUL` on an attached emulator/device.

- [ ] **Step 7: Commit persistence layer**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local
git commit -m "feat: add local book database"
```

---

### Task 5: EPUB Import Pipeline

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/Sha256Hasher.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/EpubImportService.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/Sha256HasherTest.kt`
- Create: `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/fixtures/MinimalEpubFixture.kt`
- Create: `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/importer/EpubImportServiceTest.kt`

- [ ] **Step 1: Write SHA-256 tests**

Create `Sha256HasherTest.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256HasherTest {
    @Test
    fun hashesBytesAsLowercaseHex() {
        val hash = Sha256Hasher.hash("easyreader".byteInputStream())
        assertEquals("d6c0d395dac14f910a802fd9a2d53cf18f1535ed8e2db79c23e83fb3bc72d7c7", hash)
    }
}
```

- [ ] **Step 2: Implement hasher**

Create `Sha256Hasher.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.importer

import java.io.InputStream
import java.security.MessageDigest

object Sha256Hasher {
    fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        input.use {
            while (true) {
                val read = it.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
```

- [ ] **Step 3: Add minimal EPUB fixture generator**

Create `MinimalEpubFixture.kt` that writes a valid tiny EPUB using `ZipOutputStream` with these entries:

- `mimetype` stored first with content `application/epub+zip`.
- `META-INF/container.xml`.
- `OEBPS/content.opf`.
- `OEBPS/chapter-1.xhtml`.
- `OEBPS/chapter-2.xhtml`.

The fixture title must be `Minimal EPUB` and author must be `EasyReader`.

- [ ] **Step 4: Write import service instrumentation tests**

Create `EpubImportServiceTest.kt` with two cases:

- Import one generated EPUB and assert one book plus two chapters are saved.
- Import the same file twice and assert `BookRepository.findDuplicate()` prevents a second book row.

- [ ] **Step 5: Implement import service**

Create `EpubImportService.kt`:

- Accept `Context`, `ContentResolver`, `BookRepository`, and Readium services.
- For each selected `Uri`, copy bytes into `files/books/{bookId}/book.epub`.
- Hash the selected content before creating the final directory.
- If hash exists, skip copy and return an import result with `duplicate = true`.
- Open copied EPUB with Readium `AssetRetriever` and `PublicationOpener`.
- Extract `metadata.title`, first author name, reading order, and matching TOC titles.
- Save `Book` and `Chapter` records in a single repository call.
- Store cover as `files/books/{bookId}/cover.jpg` only when Readium returns a cover bitmap.

Core import loop comment:

```kotlin
// 导入分两段执行：先复制和哈希，再让 Readium 解析元数据。这样重复书籍不会污染私有书库目录。
```

- [ ] **Step 6: Run import tests**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit import pipeline**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/importer app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/fixtures app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/importer
git commit -m "feat: add EPUB import pipeline"
```

---

### Task 6: Readium Services and Reader Session

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/ReaderSettingsStore.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/readium/ReadiumServices.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/readium/EpubReaderSession.kt`

- [ ] **Step 1: Implement DataStore-backed reader settings**

Create `ReaderSettingsStore.kt` with defaults:

- `fontScale = 1.0f`
- `publisherStyles = true`
- `scroll = true`
- `backgroundColor = "#FFFFFF"`
- `foregroundColor = "#1F1F1F"`

Persist these in Preferences DataStore under file name `reader_settings`.

- [ ] **Step 2: Implement Readium shared services**

Create `ReadiumServices.kt` based on Readium 3.3.0 sample app:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.reader.readium

import android.content.Context
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumServices(context: Context) {
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            assetRetriever = assetRetriever,
            httpClient = httpClient
        ),
        contentProtections = emptyList()
    )
}
```

- [ ] **Step 3: Implement reader session holder**

Create `EpubReaderSession.kt`:

- Method `open(book: Book, savedProgress: ReadingProgress?): EasyReaderResult<EpubReaderSessionState>`.
- Retrieve asset through `assetRetriever.retrieve(book.filePath.toUrl(), mediaType)`.
- Open publication through `publicationOpener.open(asset, allowUserInteraction = true)`.
- Reject restricted publications with a user-facing failure message.
- Create `EpubNavigatorFactory(publication)` and keep it in the session state.
- Parse `savedProgress.locatorJson` through `Locator.fromJSON(JSONObject(json))`.
- Expose `close()` that closes `Publication`.

Core lifecycle comment:

```kotlin
// 同一本书阅读期间 Publication 只打开一次，横滑切章只做 Navigator 导航，避免重建 WebView 造成卡顿。
```

- [ ] **Step 4: Build**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit Readium service layer**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/readium
git commit -m "feat: add Readium reader services"
```

---

### Task 7: Bookshelf UI, Batch Import, and Batch Shortcut Action

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`

- [ ] **Step 1: Wire application container**

Create `AppContainer.kt` with lazy singletons:

- Room database built from `AppDatabase`.
- `BookRepository`.
- `ReaderSettingsStore`.
- `ReadiumServices`.
- `EpubImportService`.
- `ShortcutInstaller`.

Use `applicationContext.filesDir` for imported book storage.

- [ ] **Step 2: Implement BookshelfViewModel**

State:

- `books: List<Book>`
- `selectedBookIds: Set<String>`
- `isSelecting: Boolean`
- `isImporting: Boolean`
- `message: String?`

Events:

- `importUris(uris: List<Uri>)`
- `openBook(bookId: String)`
- `toggleSelection(bookId: String)`
- `clearSelection()`
- `requestShortcutsForSelection()`

Import must run on `Dispatchers.IO`.

- [ ] **Step 3: Implement BookshelfScreen**

UI requirements:

- Use a compact top app bar with title `EasyReader`.
- Primary import button uses the system document picker with `ACTION_OPEN_DOCUMENT`, `CATEGORY_OPENABLE`, `EXTRA_ALLOW_MULTIPLE = true`, and MIME type `application/epub+zip`.
- Book list uses `LazyColumn`.
- Each row shows title, author, and last progress if present.
- Long press enters selection mode.
- In selection mode, show selected count and a shortcut action button.
- Empty state text: `导入 EPUB 后开始阅读`.

- [ ] **Step 4: Handle shortcut deep link in MainActivity**

In `MainActivity.onCreate`, parse `ShortcutContract.bookIdFromUri(intent.data)`. If present, start `ReaderActivity.createIntent(this, bookId)` and finish `MainActivity`.

- [ ] **Step 5: Build and smoke test**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

Manual smoke test:

- Launch app.
- Tap import.
- Select one or more EPUB files.
- Confirm books appear in the list.
- Long press a book and confirm selection mode appears.

- [ ] **Step 6: Commit bookshelf flow**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf
git commit -m "feat: add bookshelf import UI"
```

---

### Task 8: Reader Activity, Readium Navigator, and Progress Persistence

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivity.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderViewModel.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderChrome.kt`

- [ ] **Step 1: Implement ReaderActivity intent contract**

Register the Activity in `AndroidManifest.xml` inside `<application>`:

```xml
<activity
    android:name=".ui.reader.ReaderActivity"
    android:exported="false" />
```

`ReaderActivity` must expose:

```kotlin
companion object {
    private const val EXTRA_BOOK_ID = "book_id"

    fun createIntent(context: Context, bookId: String): Intent =
        Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
}
```

If `bookId` is missing, finish the Activity.

- [ ] **Step 2: Implement ReaderViewModel**

State:

- `book: Book?`
- `title: String`
- `chromeVisible: Boolean`
- `totalProgressText: String`
- `chapterProgressText: String`
- `errorMessage: String?`

Events:

- `load(bookId: String)`
- `onLocatorChanged(locatorJson: String, readingOrderIndex: Int, totalProgression: Double?, chapterProgression: Double?)`
- `toggleChrome()`
- `hideChromeForScroll()`
- `showChromeBriefly()`
- `saveProgressNow()`

Progress writes must be throttled to 500 ms during continuous locator updates and flushed immediately in `onStop`.

Core persistence comment:

```kotlin
// Locator 高频变化只更新内存状态，数据库写入节流，避免滚动时因为 I/O 造成掉帧。
```

- [ ] **Step 3: Implement ReaderGestureLayout**

`ReaderGestureLayout` extends `FrameLayout` and wraps the Readium fragment container plus Compose overlay. It must:

- Track `ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`, and `ACTION_CANCEL`.
- Use `ChapterSwipeDetector` for final horizontal decisions.
- Enter vertical lock when vertical movement wins.
- Trigger `onNextChapter()` or `onPreviousChapter()` once per gesture.
- Enforce 250 ms cooldown after a chapter switch.
- Call `onVerticalScrollStarted()` when vertical lock starts.

Add a Simplified Chinese comment documenting the state transitions.

- [ ] **Step 4: Create EpubNavigator from session**

In `ReaderActivity`, after `ReaderViewModel.load()` returns a session:

- Create a `FragmentContainerView`.
- Use the session `EpubNavigatorFactory` to create an EPUB navigator fragment in scrolled mode.
- Restore `initialLocator` from session state.
- Observe `navigator.currentLocator`.
- Save locator JSON and progression through `ReaderViewModel.onLocatorChanged`.
- For previous/next chapter, compute target reading order index and call navigator navigation without recreating the fragment.

- [ ] **Step 5: Implement ReaderChrome**

Compose overlay requirements:

- Respect status bar and navigation bar insets.
- Non-fullscreen mode shows a top bar with book title and back button.
- Hidden chrome keeps system bars visible.
- Bottom row shows left whole-book progress and right current-chapter progress.
- Use small stable text and fixed bottom padding to avoid shifting while scrolling.

- [ ] **Step 6: Build and manual test**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

Manual test:

- Import a multi-chapter EPUB.
- Open it from the bookshelf.
- Scroll vertically and confirm chrome hides.
- Tap content and confirm chrome appears with two percentages.
- Exit and reopen the book; confirm location is restored.

- [ ] **Step 7: Commit reader flow**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader
git commit -m "feat: add EPUB reader screen"
```

---

### Task 9: Horizontal Chapter Switching Polish

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderGestureLayout.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderActivity.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader/ReaderViewModel.kt`

- [ ] **Step 1: Add edge handling**

When swiping right on the first reading order item or left on the last item:

- Do not call navigator navigation.
- Show chrome briefly.
- Show lightweight message `已经到达边界`.
- Keep current progress unchanged.

- [ ] **Step 2: Add switch completion feedback**

After a successful chapter switch:

- Show bottom progress for 1200 ms.
- Persist the new locator immediately when `currentLocator` emits after navigation.
- Ignore further horizontal switches during the 250 ms cooldown.

- [ ] **Step 3: Manual gesture validation**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

Manual test on device:

- Slow vertical scroll in a long chapter for at least 30 seconds.
- Fast vertical flick with slight diagonal movement.
- Slow horizontal drag from any screen position.
- Fast horizontal fling from any screen position.
- Repeat at first and last chapter boundaries.

Expected: vertical movement does not switch chapters; horizontal movement switches once; boundary swipes do not create blank screens.

- [ ] **Step 4: Commit gesture polish**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/reader
git commit -m "feat: polish reader chapter switching"
```

---

### Task 10: Pinned Shortcuts

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/shortcut/ShortcutInstaller.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`

- [ ] **Step 1: Implement ShortcutInstaller**

Create `ShortcutInstaller.kt`:

- Use `ShortcutManagerCompat.isRequestPinShortcutSupported(context)`.
- For each selected book, build `ShortcutInfoCompat`.
- Shortcut label is book title.
- Intent action is `Intent.ACTION_VIEW`.
- Intent data is `ShortcutContract.bookUri(book.id)`.
- Icon uses cover bitmap when available, otherwise app icon.
- Request pinned shortcuts sequentially, not concurrently.
- Record each requested shortcut through `BookRepository.recordShortcut`.

Core shortcut comment:

```kotlin
// Android 不允许应用静默批量放置桌面图标，因此这里按队列逐个请求 Launcher 确认。
```

- [ ] **Step 2: Wire bookshelf action**

When the user taps the shortcut action in selection mode:

- If launcher does not support pinned shortcuts, show `当前桌面不支持添加快捷方式`.
- If no book is selected, show `请选择书籍`.
- After requests are sent, clear selection and show `已发送桌面快捷方式请求`.

- [ ] **Step 3: Build and manual shortcut test**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

Manual test:

- Select two books.
- Tap add shortcut.
- Confirm the launcher asks for each shortcut.
- Open a shortcut.
- Confirm the app opens directly into that book and restores progress.

- [ ] **Step 4: Commit shortcut support**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/shortcut app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf
git commit -m "feat: add pinned book shortcuts"
```

---

### Task 11: Final Verification and README Update

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README**

README must include:

- Project goal.
- Current MVP scope.
- Build prerequisites: JDK 21, Android SDK 35, Gradle wrapper.
- Build command.
- Test command.
- First-run note: import EPUB files through the in-app import button.

- [ ] **Step 2: Run unit tests**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run debug build**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run instrumentation tests when a device is available**

Run:

```bash
env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Expected with connected emulator/device: `BUILD SUCCESSFUL`.

If no device is connected, record the exact Gradle message and keep unit tests plus debug build as required verification for the commit.

- [ ] **Step 5: Manual acceptance checklist**

Verify on an Android phone or emulator:

- Batch import imports multiple EPUB files.
- Duplicate import does not create duplicate books.
- Bookshelf opens a selected book.
- Long vertical chapter scrolling stays smooth.
- Horizontal swipe from any content position switches chapters.
- Vertical diagonal scrolling does not accidentally switch chapters.
- Reader chrome hides while scrolling and appears on tap.
- Bottom left percentage is whole-book progress.
- Bottom right percentage is current-chapter progress.
- App keeps Android system status/navigation bars visible.
- Exiting and reopening restores the last locator.
- Desktop shortcut opens the selected book directly.

- [ ] **Step 6: Commit README and verification notes**

```bash
git add README.md
git commit -m "docs: document Android MVP workflow"
```

---

## Self-Review

Spec coverage:

- EPUB-only scope is covered by Tasks 5, 6, and 8.
- Batch import is covered by Tasks 5 and 7.
- Local bookshelf is covered by Task 7.
- Readium WebView rendering with native Compose chrome is covered by Tasks 6 and 8.
- Vertical scrolling only, no pagination, is covered by Tasks 6 and 8.
- Horizontal chapter switching from any position is covered by Tasks 3, 8, and 9.
- Dual progress display is covered by Tasks 2 and 8.
- Progress persistence by Locator JSON is covered by Tasks 4, 6, and 8.
- Pinned shortcut creation and deep-link resume are covered by Tasks 2, 7, and 10.
- Performance requirements are covered by Readium session reuse, throttled progress writes, and manual acceptance in Tasks 8, 9, and 11.

Placeholder scan:

- No unresolved placeholders are intentionally left in this plan.
- Every task names concrete files, commands, expected outputs, and commit points.

Type consistency:

- Package name is consistently `io.github.luoyuxiaoxiao.easyreader`.
- Domain model names are consistently `Book`, `Chapter`, `ReadingProgress`, and `ReaderProgressPercentages`.
- Shortcut contract consistently uses `easyreader://book/{bookId}`.
- Database tables match the approved design: `books`, `chapters`, `reading_progress`, and `shortcuts`.
