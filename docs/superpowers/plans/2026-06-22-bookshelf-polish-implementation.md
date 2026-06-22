# Bookshelf Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved bookshelf polish: less sensitive chapter swipes, thicker progress bars, user-selectable bookshelf sorting, simpler grouping rules, correct series back navigation, and delete actions for books/custom rules.

**Architecture:** Keep changes inside existing EasyReader layers. Domain code owns grouping, sorting, and natural-order parsing; DataStore owns bookshelf UI settings; ViewModel coordinates persisted settings and repository actions; Compose renders the menu/dialogs and handles back navigation.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Room, DataStore Preferences, kotlinx.serialization, JUnit4, AndroidX instrumented tests.

---

## File Structure

- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt`
  - Raise horizontal swipe thresholds.
- Modify `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetectorTest.kt`
  - Cover the stricter threshold.
- Create `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/NaturalSort.kt`
  - Parse and compare natural sort keys such as `[S5_02_01]` and `10`.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfModels.kt`
  - Add `createdAt` to bookshelf books, bookshelf sort mode, grouping rule kind, simple-rule fields, and optional natural sort key on series.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGrouping.kt`
  - Apply simple `[S...]` rules, natural sorting, and configurable top-level sorting.
- Modify `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGroupingTest.kt`
  - Add regression tests for magic-index samples, manual override, and sort modes.
- Create `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/BookshelfSettingsStore.kt`
  - Persist sort mode and direction.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStore.kt`
  - Serialize new rule fields and support deleting custom rules.
- Modify `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStoreTest.kt`
  - Confirm backward-compatible serialization and simple-rule persistence.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt`
  - Provide `BookshelfSettingsStore`.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt`
  - Pass the new store into `BookshelfViewModel.factory`.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookDao.kt`
  - Add book deletion query.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt`
  - Delete selected books and private imported files.
- Modify `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt`
  - Verify deleting a book cascades chapters, progress, and shortcut records.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
  - Combine repository snapshots, grouping rules, and bookshelf settings; expose sorting/rule/delete actions.
- Modify `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModelTest.kt`
  - Verify sorting settings reach `buildBookshelfEntries`.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`
  - Add the “整理” menu, back handling, thicker progress bars, delete confirmations, simple grouping rule UI, and custom-rule delete buttons.

---

### Task 1: Stricter Chapter Swipe Threshold

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt`
- Modify: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetectorTest.kt`

- [ ] **Step 1: Add failing gesture threshold tests**

Add these tests to `ChapterSwipeDetectorTest`:

```kotlin
@Test
fun oldMediumHorizontalSwipeNoLongerSwitchesChapter() {
    val event = detector.evaluate(startXPx = 540f, dxPx = -170f, dyPx = 8f, velocityXPxPerSecond = -900f)

    assertEquals(ChapterSwipeDecision.KeepReading, event)
}

@Test
fun longerHorizontalSwipeStillSwitchesChapter() {
    val next = detector.evaluate(startXPx = 540f, dxPx = -230f, dyPx = 20f, velocityXPxPerSecond = -1000f)
    val previous = detector.evaluate(startXPx = 540f, dxPx = 230f, dyPx = 20f, velocityXPxPerSecond = 1000f)

    assertEquals(ChapterSwipeDecision.NextChapter, next)
    assertEquals(ChapterSwipeDecision.PreviousChapter, previous)
}
```

Update the existing `nearHorizontalSwipeSwitchesChapterWithoutDiagonalMovement` test to use `230f` instead of `170f`.

- [ ] **Step 2: Run the focused gesture test and verify failure**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:testDebugUnitTest --tests 'io.github.luoyuxiaoxiao.easyreader.reader.gesture.ChapterSwipeDetectorTest'
```

Expected: `oldMediumHorizontalSwipeNoLongerSwitchesChapter` fails because `170f` still triggers the old fling path.

- [ ] **Step 3: Raise thresholds in implementation**

Change `ChapterSwipeDetector` constants:

```kotlin
private val minHorizontalDistancePx = max(72f * density, screenWidthPx * 0.18f)
private val fastDistancePx = 72f * density
private val fastVelocityPxPerSecond = 900f * density
```

Keep the Chinese comments about system back and direction constraints.

- [ ] **Step 4: Run gesture tests and verify pass**

Run the same focused test command.

Expected: `ChapterSwipeDetectorTest` passes.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetector.kt app/src/test/java/io/github/luoyuxiaoxiao/easyreader/reader/gesture/ChapterSwipeDetectorTest.kt
git commit -m "fix: reduce accidental chapter swipes"
```

---

### Task 2: Natural Sort and Bookshelf Sort Settings

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/NaturalSort.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfModels.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGrouping.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/BookshelfSettingsStore.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- Modify: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGroupingTest.kt`
- Modify: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModelTest.kt`

- [ ] **Step 1: Add failing natural sort and sort-mode tests**

Add to `BookshelfGroupingTest`:

```kotlin
@Test
fun naturalSortOrdersMagicIndexPrefixes() {
    val sorted = listOf("[S6_24.12.10]后记", "[S1_02]第二卷", "[S1_01]第一卷", "[S5_02_01]外传")
        .sortedWith(NaturalSort.comparator())

    assertEquals(listOf("[S1_01]第一卷", "[S1_02]第二卷", "[S5_02_01]外传", "[S6_24.12.10]后记"), sorted)
}

@Test
fun titleSortUsesNaturalOrderForSingleBooks() {
    val entries = BookshelfGrouping.entries(
        books = listOf(
            book(id = "10", title = "Book 10", updatedAt = 300L),
            book(id = "2", title = "Book 2", updatedAt = 200L),
            book(id = "1", title = "Book 1", updatedAt = 100L),
        ),
        customRules = emptyList(),
        sortMode = BookshelfSortMode.Title,
        sortAscending = true,
    )

    assertEquals(listOf("Book 1", "Book 2", "Book 10"), entries.map { (it as BookshelfEntry.SingleBook).book.title })
}

@Test
fun recentSortCanBeDescending() {
    val entries = BookshelfGrouping.entries(
        books = listOf(
            book(id = "old", title = "Old", updatedAt = 100L),
            book(id = "new", title = "New", updatedAt = 300L),
        ),
        customRules = emptyList(),
        sortMode = BookshelfSortMode.Recent,
        sortAscending = false,
    )

    assertEquals(listOf("New", "Old"), entries.map { (it as BookshelfEntry.SingleBook).book.title })
}
```

Extend the private `book` helper with `createdAt: Long = 0L` and `updatedAt: Long = 0L`.

- [ ] **Step 2: Run focused grouping tests and verify failure**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:testDebugUnitTest --tests 'io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGroupingTest'
```

Expected: compile fails because `NaturalSort`, `BookshelfSortMode`, and new `entries` parameters do not exist.

- [ ] **Step 3: Create natural sort utility**

Create `NaturalSort.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.bookshelf

object NaturalSort {
    fun comparator(): Comparator<String> = Comparator { left, right -> compare(left, right) }

    fun compare(left: String, right: String): Int {
        val leftParts = tokenize(left)
        val rightParts = tokenize(right)
        val count = minOf(leftParts.size, rightParts.size)
        for (index in 0 until count) {
            val result = leftParts[index].compareTo(rightParts[index])
            if (result != 0) return result
        }
        return leftParts.size.compareTo(rightParts.size)
    }

    fun tokenize(value: String): List<NaturalToken> {
        val tokens = mutableListOf<NaturalToken>()
        val pattern = Regex("""\d+|\D+""")
        pattern.findAll(value).forEach { match ->
            val text = match.value
            val number = text.toLongOrNull()
            tokens += if (number != null) {
                NaturalToken.Number(number, text.length)
            } else {
                NaturalToken.Text(text.lowercase())
            }
        }
        return tokens
    }
}

sealed interface NaturalToken : Comparable<NaturalToken> {
    data class Number(val value: Long, val width: Int) : NaturalToken
    data class Text(val value: String) : NaturalToken

    override fun compareTo(other: NaturalToken): Int =
        when {
            this is Number && other is Number -> value.compareTo(other.value).takeIf { it != 0 } ?: width.compareTo(other.width)
            this is Text && other is Text -> value.compareTo(other.value)
            this is Number -> -1
            else -> 1
        }
}
```

- [ ] **Step 4: Add sort models**

In `BookshelfModels.kt`, add:

```kotlin
data class BookshelfBook(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val metadataSeries: String?,
    val metadataSeriesIndex: Double?,
    val manualSeries: String?,
    val manualSeriesIndex: Double?,
    val createdAt: Long,
    val lastOpenedAt: Long?,
    val updatedAt: Long,
    val totalProgression: Double?,
)

enum class BookshelfSortMode {
    Recent,
    Added,
    Title,
    Series,
}

data class BookshelfSettings(
    val sortMode: BookshelfSortMode = BookshelfSortMode.Recent,
    val sortAscending: Boolean = false,
)
```

Add `val sortKey: String? = null` to `BookshelfSeries`.

- [ ] **Step 5: Implement configurable grouping sort**

Update `BookshelfGrouping.entries` signature:

```kotlin
fun entries(
    books: List<BookshelfBook>,
    customRules: List<SeriesGroupingRule>,
    disabledBuiltInRuleIds: Set<String> = emptySet(),
    sortMode: BookshelfSortMode = BookshelfSortMode.Recent,
    sortAscending: Boolean = false,
): List<BookshelfEntry>
```

When creating `BookshelfSeries`, pass `sortKey = sortSeriesBooks(group, rules).firstOrNull()?.title`.

Replace the final `.sortedWith(compareBy({ entrySortTime(it) }, { entryTitle(it) }))` with:

```kotlin
.let { entries -> sortEntries(entries, sortMode, sortAscending) }
```

Add helpers:

```kotlin
private fun sortEntries(
    entries: List<BookshelfEntry>,
    sortMode: BookshelfSortMode,
    ascending: Boolean,
): List<BookshelfEntry> {
    val comparator = when (sortMode) {
        BookshelfSortMode.Recent -> compareBy<BookshelfEntry> { entryRecentAt(it) }
        BookshelfSortMode.Added -> compareBy { entryCreatedAt(it) }
        BookshelfSortMode.Title -> Comparator { left, right -> NaturalSort.compare(entryTitle(left), entryTitle(right)) }
        BookshelfSortMode.Series -> Comparator { left, right -> NaturalSort.compare(entrySeriesSortText(left), entrySeriesSortText(right)) }
    }
    val sorted = entries.sortedWith(comparator.thenBy { entryTitle(it) })
    return if (ascending) sorted else sorted.reversed()
}

private fun entryRecentAt(entry: BookshelfEntry): Long =
    when (entry) {
        is BookshelfEntry.Series -> entry.series.books.maxOf { it.lastOpenedAt ?: it.updatedAt }
        is BookshelfEntry.SingleBook -> entry.book.lastOpenedAt ?: entry.book.updatedAt
    }

private fun entryCreatedAt(entry: BookshelfEntry): Long =
    when (entry) {
        is BookshelfEntry.Series -> entry.series.books.minOf { it.createdAt }
        is BookshelfEntry.SingleBook -> entry.book.createdAt
    }

private fun entrySeriesSortText(entry: BookshelfEntry): String =
    when (entry) {
        is BookshelfEntry.Series -> entry.series.sortKey ?: entry.series.title
        is BookshelfEntry.SingleBook -> entry.book.title
    }
```

Keep a Chinese comment near `sortEntries` explaining that top-level sorting is user controlled while series-internal order remains stable.

- [ ] **Step 6: Persist bookshelf settings**

Create `BookshelfSettingsStore.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSettings
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bookshelfSettingsDataStore by preferencesDataStore(name = "bookshelf_settings")

class BookshelfSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.bookshelfSettingsDataStore

    val settings: Flow<BookshelfSettings> = dataStore.data.map { preferences ->
        BookshelfSettings(
            sortMode = preferences[SORT_MODE]?.let { runCatching { BookshelfSortMode.valueOf(it) }.getOrNull() }
                ?: BookshelfSortMode.Recent,
            sortAscending = preferences[SORT_ASCENDING] ?: false,
        )
    }

    suspend fun setSortMode(mode: BookshelfSortMode) {
        dataStore.edit { preferences -> preferences[SORT_MODE] = mode.name }
    }

    suspend fun setSortAscending(ascending: Boolean) {
        dataStore.edit { preferences -> preferences[SORT_ASCENDING] = ascending }
    }

    private companion object {
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
    }
}
```

- [ ] **Step 7: Wire settings through DI and ViewModel**

In `AppContainer`, add:

```kotlin
val bookshelfSettingsStore: BookshelfSettingsStore by lazy {
    BookshelfSettingsStore(applicationContext)
}
```

In `MainActivity`, pass `bookshelfSettingsStore = appContainer.bookshelfSettingsStore`.

In `BookshelfViewModel.factory`, add the parameter and constructor field.

Extend `BookshelfUiState`:

```kotlin
val sortMode: BookshelfSortMode = BookshelfSortMode.Recent,
val sortAscending: Boolean = false,
```

Change the initial `combine` to include `bookshelfSettingsStore.settings`, then pass `settings.sortMode` and `settings.sortAscending` to `buildBookshelfEntries`.

When mapping `Book` to `BookshelfBook`, include:

```kotlin
createdAt = book.createdAt,
```

Add ViewModel methods:

```kotlin
fun setSortMode(mode: BookshelfSortMode) {
    viewModelScope.launch(Dispatchers.IO) {
        bookshelfSettingsStore.setSortMode(mode)
    }
}

fun setSortAscending(ascending: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
        bookshelfSettingsStore.setSortAscending(ascending)
    }
}
```

- [ ] **Step 8: Run focused unit tests**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:testDebugUnitTest --tests 'io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGroupingTest' --tests 'io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfViewModelTest'
```

Expected: both focused test classes pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/BookshelfSettingsStore.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGroupingTest.kt app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModelTest.kt
git commit -m "feat: add bookshelf sorting settings"
```

---

### Task 3: Simple Grouping Rule Template and Rule Deletion

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfModels.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGrouping.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStore.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- Modify: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGroupingTest.kt`
- Modify: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStoreTest.kt`

- [ ] **Step 1: Add failing tests for simple `[S...]` rules**

Add to `BookshelfGroupingTest`:

```kotlin
@Test
fun simpleMagicPrefixRuleGroupsLargeSeriesAndSortsByPrefix() {
    val rule = SeriesGroupingRule.magicPrefix(
        id = "magic-index",
        name = "魔禁大系列",
        seriesName = "魔法禁书目录",
        priority = 0,
    )
    val entries = BookshelfGrouping.entries(
        books = listOf(
            book(id = "s2", title = "[S2_01]新约 某魔法的禁书目录 01X"),
            book(id = "s1", title = "[S1_01]某魔法的禁书目录 01X"),
            book(id = "s5", title = "[S5_02_01]某科学的超电磁炮SS 学艺都市篇X"),
        ),
        customRules = listOf(rule),
        sortMode = BookshelfSortMode.Series,
        sortAscending = true,
    )

    val series = entries.single() as BookshelfEntry.Series
    assertEquals("魔法禁书目录", series.series.title)
    assertEquals(listOf("s1", "s2", "s5"), series.series.books.map { it.id })
}
```

Add to `SeriesGroupingRuleStoreTest`:

```kotlin
@Test
fun encodesAndDecodesMagicPrefixRule() {
    val settings = SeriesGroupingRuleSettings(
        customRules = listOf(
            SeriesGroupingRule.magicPrefix(
                id = "magic-index",
                name = "魔禁大系列",
                seriesName = "魔法禁书目录",
                priority = 0,
            )
        )
    )

    val decoded = SeriesGroupingRuleSerializer.decode(SeriesGroupingRuleSerializer.encode(settings))

    assertEquals(settings, decoded)
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:testDebugUnitTest --tests 'io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGroupingTest' --tests 'io.github.luoyuxiaoxiao.easyreader.data.settings.SeriesGroupingRuleStoreTest'
```

Expected: compile fails because `magicPrefix` and the new rule fields do not exist.

- [ ] **Step 3: Extend rule model**

In `BookshelfModels.kt`, add:

```kotlin
enum class SeriesGroupingRuleKind {
    Regex,
    MagicPrefix,
}
```

Extend `SeriesGroupingRule`:

```kotlin
val kind: SeriesGroupingRuleKind = SeriesGroupingRuleKind.Regex,
val seriesOverride: String? = null,
```

Add companion factory:

```kotlin
fun magicPrefix(id: String, name: String, seriesName: String, priority: Int): SeriesGroupingRule =
    SeriesGroupingRule(
        id = id,
        name = name,
        pattern = """^\[S[\d_.]+\]""",
        enabled = true,
        priority = priority,
        builtIn = false,
        kind = SeriesGroupingRuleKind.MagicPrefix,
        seriesOverride = seriesName,
    )
```

Add validation overload:

```kotlin
fun validate(rule: SeriesGroupingRule): RuleValidationResult =
    when (rule.kind) {
        SeriesGroupingRuleKind.MagicPrefix ->
            if (rule.seriesOverride.isNullOrBlank()) RuleValidationResult.Invalid("大系列名不能为空") else RuleValidationResult.Valid
        SeriesGroupingRuleKind.Regex -> validate(rule.pattern)
    }
```

Regex rules keep requiring `(?<series>...)`.

- [ ] **Step 4: Implement simple rule matching**

In `BookshelfGrouping.matchSeries`, branch by `rule.kind`:

```kotlin
private fun matchSeries(rule: SeriesGroupingRule, title: String): RuleMatch? =
    when (rule.kind) {
        SeriesGroupingRuleKind.MagicPrefix -> matchMagicPrefix(rule, title)
        SeriesGroupingRuleKind.Regex -> matchRegex(rule, title)
    }
```

Add:

```kotlin
private fun matchMagicPrefix(rule: SeriesGroupingRule, title: String): RuleMatch? {
    val series = rule.seriesOverride.cleanSeries() ?: return null
    val prefix = Regex("""^\[S([\d_.]+)\]""").find(title)?.groupValues?.getOrNull(1) ?: return null
    return RuleMatch(series = series, sortText = prefix)
}
```

Change `RuleMatch` to:

```kotlin
private data class RuleMatch(val series: String, val index: Double? = null, val sortText: String? = null)
```

When sorting series books, compare `sortText` with `NaturalSort.compare` before title.

- [ ] **Step 5: Update serializer and store deletion**

In `StoredSeriesGroupingRule`, add:

```kotlin
val kind: SeriesGroupingRuleKind = SeriesGroupingRuleKind.Regex,
val seriesOverride: String? = null,
```

Update `toStored()` and `toDomain()` to map these fields.

Add to `SeriesGroupingRuleStore`:

```kotlin
suspend fun deleteCustomRule(ruleId: String) {
    val current = settings.first()
    updateSettings(current.copy(customRules = current.customRules.filterNot { it.id == ruleId }))
}
```

- [ ] **Step 6: Add ViewModel deletion action**

Add to `BookshelfViewModel`:

```kotlin
fun deleteCustomRule(ruleId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        seriesGroupingRuleStore.deleteCustomRule(ruleId)
    }
}
```

Update `addCustomRule` to call `SeriesGroupingRule.validate(rule)` so simple rules and regex rules use the correct validation path.

- [ ] **Step 7: Run focused tests**

Run the same focused command from Step 2.

Expected: `BookshelfGroupingTest` and `SeriesGroupingRuleStoreTest` pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStore.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGroupingTest.kt app/src/test/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStoreTest.kt
git commit -m "feat: add simple series grouping rules"
```

---

### Task 4: Book Deletion Data Path

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookDao.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- Modify: `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt`

- [ ] **Step 1: Add failing database cascade test**

Add to `AppDatabaseTest`:

```kotlin
@Test
fun deletingBookCascadesBookGraph() = runBlocking {
    val book = BookEntity(
        id = "book-delete",
        title = "Delete Me",
        author = null,
        filePath = "/books/book-delete/book.epub",
        sha256 = "hash-delete",
        coverPath = null,
        metadataSeries = null,
        metadataSeriesIndex = null,
        manualSeries = null,
        manualSeriesIndex = null,
        createdAt = 100L,
        updatedAt = 200L,
        lastOpenedAt = null,
    )
    database.bookDao().upsert(book)
    database.chapterDao().replaceChapters(
        book.id,
        listOf(ChapterEntity("chapter", book.id, 0, "chapter.xhtml", "Chapter", true))
    )
    database.readingProgressDao().upsert(
        ReadingProgressEntity(book.id, "{}", 0, 0.5, 0.5, 300L)
    )
    database.shortcutDao().upsert(
        ShortcutEntity(book.id, "shortcut", 400L, 500L)
    )

    database.bookDao().deleteByIds(listOf(book.id))

    assertEquals(null, database.bookDao().findById(book.id))
    assertEquals(emptyList<ChapterEntity>(), database.chapterDao().findByBookId(book.id))
    assertEquals(null, database.readingProgressDao().find(book.id))
    assertEquals(null, database.shortcutDao().find(book.id))
}
```

- [ ] **Step 2: Run instrumented database test and verify failure**

Run:

```bash
timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.luoyuxiaoxiao.easyreader.data.local.AppDatabaseTest
```

Expected: compile fails because `deleteByIds` does not exist.

- [ ] **Step 3: Add DAO deletion query**

Add to `BookDao`:

```kotlin
@Query("DELETE FROM books WHERE id IN (:bookIds)")
suspend fun deleteByIds(bookIds: List<String>)
```

- [ ] **Step 4: Add repository deletion**

Add to `BookRepository`:

```kotlin
suspend fun deleteBooks(bookIds: List<String>) {
    if (bookIds.isEmpty()) return
    val books = bookIds.mapNotNull { bookDao.findById(it)?.toDomain() }
    database.withTransaction {
        bookDao.deleteByIds(bookIds)
    }
    books.forEach { book ->
        deleteImportedFiles(book)
    }
}

private fun deleteImportedFiles(book: Book) {
    val epubFile = java.io.File(book.filePath)
    val directory = epubFile.parentFile
    // 只删除应用导入副本所在目录；用户下载目录中的原始 EPUB 不在这里。
    if (epubFile.name == "book.epub" && directory?.name == book.id) {
        directory.deleteRecursively()
    } else {
        epubFile.delete()
        book.coverPath?.let { java.io.File(it).delete() }
    }
}
```

- [ ] **Step 5: Add ViewModel delete action**

Add to `BookshelfViewModel`:

```kotlin
fun deleteSelectedBooks() {
    val selected = uiState.value.selectedBookIds
    if (selected.isEmpty()) {
        showMessage("请选择书籍")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        bookRepository.deleteBooks(selected.toList())
        _uiState.update {
            it.copy(
                selectedBookIds = emptySet(),
                openedSeriesId = null,
                message = "已删除 ${selected.size} 本书",
            )
        }
    }
}
```

- [ ] **Step 6: Run database test**

Run the same instrumented command from Step 2.

Expected: `AppDatabaseTest` passes.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookDao.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt
git commit -m "feat: delete imported books"
```

---

### Task 5: Bookshelf UI Menu, Back Handling, and Progress Bars

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`

- [ ] **Step 1: Wire new callbacks into `BookshelfScreen`**

In `BookshelfScreen`, pass:

```kotlin
onSetSortMode = viewModel::setSortMode,
onSetSortAscending = viewModel::setSortAscending,
onDeleteSelectedBooks = viewModel::deleteSelectedBooks,
onDeleteCustomRule = viewModel::deleteCustomRule,
```

Add matching parameters to `BookshelfContent`.

- [ ] **Step 2: Add Compose back handling**

Import:

```kotlin
import androidx.activity.compose.BackHandler
```

Inside `BookshelfContent`, after `openedSeries` is computed, add:

```kotlin
BackHandler(enabled = state.isSelecting || openedSeries != null) {
    when {
        state.isSelecting -> onClearSelection()
        openedSeries != null -> onCloseSeries()
    }
}
```

Dialog dismissal remains handled by each `AlertDialog` `onDismissRequest`.

- [ ] **Step 3: Replace top-level rule button with “整理” menu**

Add imports:

```kotlin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
```

Add state:

```kotlin
var showOrganizeMenu by remember { mutableStateOf(false) }
```

In top app bar actions when not selecting and not in opened series, replace direct `归组规则` button with:

```kotlin
Box {
    TextButton(onClick = { showOrganizeMenu = true }) {
        Text("整理")
    }
    DropdownMenu(
        expanded = showOrganizeMenu,
        onDismissRequest = { showOrganizeMenu = false },
    ) {
        Text("排序方式", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Medium)
        BookshelfSortMode.values().forEach { mode ->
            DropdownMenuItem(
                text = { Text(sortModeLabel(mode)) },
                onClick = {
                    onSetSortMode(mode)
                    showOrganizeMenu = false
                },
            )
        }
        DropdownMenuItem(
            text = { Text(if (state.sortAscending) "切换为降序" else "切换为升序") },
            onClick = {
                onSetSortAscending(!state.sortAscending)
                showOrganizeMenu = false
            },
        )
        DropdownMenuItem(
            text = { Text("自动归组规则") },
            onClick = {
                showRuleDialog = true
                showOrganizeMenu = false
            },
        )
    }
}
```

Add helper:

```kotlin
private fun sortModeLabel(mode: BookshelfSortMode): String =
    when (mode) {
        BookshelfSortMode.Recent -> "最近阅读"
        BookshelfSortMode.Added -> "添加日期"
        BookshelfSortMode.Title -> "标题"
        BookshelfSortMode.Series -> "系列顺序"
    }
```

- [ ] **Step 4: Add delete action for selected books**

In selecting actions, add before “取消”:

```kotlin
TextButton(onClick = { showDeleteBooksDialog = true }) {
    Text("删除")
}
```

Add state:

```kotlin
var showDeleteBooksDialog by remember { mutableStateOf(false) }
```

Add confirmation dialog:

```kotlin
if (showDeleteBooksDialog) {
    AlertDialog(
        onDismissRequest = { showDeleteBooksDialog = false },
        title = { Text("删除图书") },
        text = { Text("将从 EasyReader 删除已选 ${state.selectedBookIds.size} 本书。手机下载目录中的原始 EPUB 不会被删除。") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDeleteSelectedBooks()
                    showDeleteBooksDialog = false
                }
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = { showDeleteBooksDialog = false }) { Text("取消") }
        },
    )
}
```

- [ ] **Step 5: Add simple rule fields and custom rule delete button**

Add state:

```kotlin
var simpleSeriesName by remember { mutableStateOf("") }
var ruleToDelete by remember { mutableStateOf<SeriesGroupingRule?>(null) }
```

In the rule dialog, add a “简单规则” section before custom regex fields:

```kotlin
Text("简单规则", fontWeight = FontWeight.Medium)
TextField(
    value = simpleSeriesName,
    onValueChange = { simpleSeriesName = it },
    label = { Text("大系列名") },
    singleLine = true,
)
Text(
    text = "用于 [S1_01]、[S5_02_01] 这类前缀，自动归为同一个大系列。",
    style = MaterialTheme.typography.bodySmall,
)
```

In custom rule rows, add:

```kotlin
TextButton(onClick = { ruleToDelete = rule }) {
    Text("删除")
}
```

Add delete confirmation:

```kotlin
ruleToDelete?.let { rule ->
    AlertDialog(
        onDismissRequest = { ruleToDelete = null },
        title = { Text("删除规则") },
        text = { Text("删除自定义规则：${rule.name}") },
        confirmButton = {
            TextButton(
                onClick = {
                    onDeleteCustomRule(rule.id)
                    ruleToDelete = null
                }
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = { ruleToDelete = null }) { Text("取消") }
        },
    )
}
```

Change save action: if `simpleSeriesName` is not blank, create `SeriesGroupingRule.magicPrefix(...)`; otherwise create the advanced regex rule.

- [ ] **Step 6: Thicken progress bars**

In `BookGridItem` and `SeriesStackItem`, change progress indicator height:

```kotlin
.height(8.dp)
```

Keep `BookshelfProgressGreen` as `Color(0xFF18A558)`.

- [ ] **Step 7: Run Compose compile through unit build**

Run:

```bash
timeout 120s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:testDebugUnitTest
```

Expected: unit tests compile and pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt
git commit -m "feat: add bookshelf organize menu"
```

---

### Task 6: Full Verification and Device Smoke Test

**Files:**
- No planned source edits unless verification finds a defect.

- [ ] **Step 1: Run full unit test suite**

Run:

```bash
timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:testDebugUnitTest
```

Expected: all debug unit tests pass.

- [ ] **Step 2: Build debug APK**

Run:

```bash
timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run database instrumented test if device is connected**

Run:

```bash
adb devices
timeout 180s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.luoyuxiaoxiao.easyreader.data.local.AppDatabaseTest
```

Expected: device listed as `device`, `AppDatabaseTest` passes.

- [ ] **Step 4: Install debug build for smoke test when compatible**

The connected phone currently has release `v0.2.0`; debug install may conflict with release signing. If debug install is needed, use an emulator or uninstall release only after user confirmation.

Smoke checklist:

```text
1. Open bookshelf.
2. Open a series.
3. Use system back or edge back; result should return to bookshelf, not exit App.
4. Open 整理 menu.
5. Change sort mode and direction; visible order changes consistently.
6. Open 自动归组规则; create a simple [S编号] rule with 大系列名=魔法禁书目录.
7. Delete that custom rule.
8. Long-press a book, select it, open delete confirmation, cancel.
```

- [ ] **Step 5: Final status check**

Run:

```bash
git status --short --branch
git log --oneline -6
```

Expected: branch contains the task commits and has no unstaged changes.

---

## Self-Review

- Spec coverage:
  - Gesture threshold: Task 1.
  - Progress bar thickness: Task 5.
  - Sort menu and persisted sorting: Tasks 2 and 5.
  - Simple grouping rule with advanced regex retained: Tasks 3 and 5.
  - Manual grouping remains normal path: Task 5 keeps selection actions.
  - Series back behavior: Task 5.
  - Book deletion: Task 4 and Task 5.
  - Custom rule deletion: Task 3 and Task 5.
- Placeholder scan:
  - No empty sections, no undefined tasks, no incomplete command placeholders.
- Type consistency:
  - `BookshelfSortMode`, `BookshelfSettings`, `SeriesGroupingRuleKind`, and `SeriesGroupingRule.magicPrefix` are introduced before later tasks use them.
