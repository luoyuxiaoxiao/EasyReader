# Bookshelf UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Perfect Viewer-style bookshelf with series stacks, local EPUB cover/series metadata, user-configurable regex grouping rules, manual series assignment, and progress bars backed by saved reader total progress.

**Architecture:** Keep the bookshelf feature split into pure domain grouping logic, persistence adapters, import metadata extraction, ViewModel state shaping, and Compose rendering. The UI consumes `BookshelfEntry` objects and never parses titles or reads progress directly. Manual series assignment overrides EPUB metadata, custom regex rules, and built-in rules.

**Tech Stack:** Kotlin, AndroidX Compose Material3/Foundation, Room, DataStore Preferences, kotlinx.serialization JSON, Android BitmapFactory, JUnit/Robolectric, Android instrumentation tests.

---

## File Structure

- Create `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfModels.kt`
  - Domain-only models for bookshelf entries, progress normalization, grouping rules, and rule validation.
- Create `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGrouping.kt`
  - Pure grouping engine: manual series > EPUB series > custom regex > built-in regex > single book.
- Create `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStore.kt`
  - DataStore-backed local configuration for custom rules and disabled built-in rules.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/book/BookModels.kt`
  - Add series fields and manual series fields to `Book`.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookEntities.kt`
  - Add Room columns for series metadata and manual series assignment.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookDao.kt`
  - Add update queries for manual series and observe query for books joined with progress.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/ReadingProgressDao.kt`
  - Keep existing point query; bookshelf progress uses the `BookDao.observeBookshelfBooks()` join.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabase.kt`
  - Bump schema version to 2 and add migration.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt`
  - Expose bookshelf book snapshots with saved total progress and manual series updates.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/EpubImportService.kt`
  - Extract cover image and series metadata during import.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt`
  - Provide `SeriesGroupingRuleStore`.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
  - Combine books/progress/rules into `BookshelfEntry` UI state and expose manual series actions.
- Modify `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`
  - Replace list with 3-column grid, series stack items, series detail grid, rule editor entry, and manual series dialog.
- Create or modify tests:
  - `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGroupingTest.kt`
  - `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStoreTest.kt`
  - `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModelTest.kt`
  - `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt`
  - `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/importer/EpubImportServiceTest.kt`
  - `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/fixtures/MinimalEpubFixture.kt`

## Verification Baseline

Use the existing Java and Android SDK environment:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --no-daemon
```

Expected final result: `BUILD SUCCESSFUL`.

Use instrumentation and assemble when tasks touch Room schema or Android import code:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:compileDebugAndroidTestKotlin :app:assembleDebug --no-daemon
```

Expected final result: `BUILD SUCCESSFUL`.

---

### Task 1: Pure Bookshelf Grouping Domain

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfModels.kt`
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGrouping.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf/BookshelfGroupingTest.kt`

- [ ] **Step 1: Write failing grouping tests**

Create `BookshelfGroupingTest.kt` with these concrete cases:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfGroupingTest {
    @Test
    fun manualSeriesOverridesMetadataAndRegex() {
        val books = listOf(
            book(id = "1", title = "Fate Vol.01", metadataSeries = "Fate", manualSeries = "手动 Fate"),
            book(id = "2", title = "Fate Vol.02", metadataSeries = "Fate", manualSeries = "手动 Fate"),
        )

        val entries = BookshelfGrouping.entries(books, customRules = emptyList())

        val series = entries.single() as BookshelfEntry.Series
        assertEquals("手动 Fate", series.series.title)
        assertEquals(listOf("1", "2"), series.series.books.map { it.id })
    }

    @Test
    fun customRegexWinsOverBuiltInRegexButLosesToMetadata() {
        val custom = SeriesGroupingRule(
            id = "custom-fate",
            name = "Custom Fate",
            pattern = """(?<series>Fate stay night).+?(?<index>\d+)""",
            enabled = true,
            priority = 0,
            builtIn = false,
        )
        val books = listOf(
            book(id = "1", title = "Fate stay night [01]", metadataSeries = null),
            book(id = "2", title = "Fate stay night [02]", metadataSeries = null),
            book(id = "3", title = "UBW Vol.01", metadataSeries = "Fate UBW"),
            book(id = "4", title = "UBW Vol.02", metadataSeries = "Fate UBW"),
        )

        val entries = BookshelfGrouping.entries(books, customRules = listOf(custom))

        val titles = entries.filterIsInstance<BookshelfEntry.Series>().map { it.series.title }.sorted()
        assertEquals(listOf("Fate UBW", "Fate stay night"), titles)
    }

    @Test
    fun singleRegexCandidateRemainsSingleBook() {
        val entries = BookshelfGrouping.entries(
            books = listOf(book(id = "1", title = "孤本 Vol.01")),
            customRules = emptyList(),
        )

        assertTrue(entries.single() is BookshelfEntry.SingleBook)
    }

    @Test
    fun seriesProgressIsAverageOfClampedBookProgress() {
        val books = listOf(
            book(id = "1", title = "A Vol.01", totalProgression = 1.2),
            book(id = "2", title = "A Vol.02", totalProgression = 0.5),
            book(id = "3", title = "A Vol.03", totalProgression = null),
        )

        val series = BookshelfGrouping.entries(books, emptyList()).single() as BookshelfEntry.Series

        assertEquals(0.5, series.series.progress, 0.0001)
    }

    @Test
    fun progressAtNinetyNinePercentIsCompleted() {
        val progress = BookshelfGrouping.normalizeProgress(0.99)

        assertEquals(1.0, progress, 0.0001)
    }

    @Test
    fun invalidRuleReportsValidationError() {
        val result = SeriesGroupingRule.validate("""(?<index>\d+)""")

        assertTrue(result is RuleValidationResult.Invalid)
    }

    private fun book(
        id: String,
        title: String,
        metadataSeries: String? = null,
        manualSeries: String? = null,
        totalProgression: Double? = null,
    ) = BookshelfBook(
        id = id,
        title = title,
        author = null,
        coverPath = null,
        metadataSeries = metadataSeries,
        metadataSeriesIndex = null,
        manualSeries = manualSeries,
        manualSeriesIndex = null,
        lastOpenedAt = null,
        updatedAt = 0L,
        totalProgression = totalProgression,
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGroupingTest --no-daemon
```

Expected: FAIL with unresolved references for `BookshelfGrouping`, `BookshelfEntry`, `SeriesGroupingRule`, or `BookshelfBook`.

- [ ] **Step 3: Implement domain models**

Create `BookshelfModels.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.bookshelf

data class BookshelfBook(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val metadataSeries: String?,
    val metadataSeriesIndex: Double?,
    val manualSeries: String?,
    val manualSeriesIndex: Double?,
    val lastOpenedAt: Long?,
    val updatedAt: Long,
    val totalProgression: Double?,
)

sealed interface BookshelfEntry {
    data class Series(val series: BookshelfSeries) : BookshelfEntry
    data class SingleBook(val book: BookshelfBook, val progress: Double) : BookshelfEntry
}

data class BookshelfSeries(
    val id: String,
    val title: String,
    val books: List<BookshelfBook>,
    val progress: Double,
)

data class SeriesGroupingRule(
    val id: String,
    val name: String,
    val pattern: String,
    val enabled: Boolean,
    val priority: Int,
    val builtIn: Boolean,
) {
    companion object {
        fun validate(pattern: String): RuleValidationResult =
            runCatching {
                Regex(pattern)
                if (pattern.contains("(?<series>")) {
                    RuleValidationResult.Valid
                } else {
                    RuleValidationResult.Invalid("缺少 series 捕获组")
                }
            }.getOrElse { RuleValidationResult.Invalid(it.message ?: "正则表达式无效") }
    }
}

data class SeriesGroupingRuleSettings(
    val customRules: List<SeriesGroupingRule> = emptyList(),
    val disabledBuiltInRuleIds: Set<String> = emptySet(),
)

sealed interface RuleValidationResult {
    data object Valid : RuleValidationResult
    data class Invalid(val message: String) : RuleValidationResult
}
```

- [ ] **Step 4: Implement grouping engine**

Create `BookshelfGrouping.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.domain.bookshelf

object BookshelfGrouping {
    val builtInRules: List<SeriesGroupingRule> = listOf(
        SeriesGroupingRule("builtin-vol", "英文卷号", """(?<series>.+?)\s+(?:Vol\.?|Volume)\s*\.?(?<index>\d+(?:\.\d+)?)""", true, 100, true),
        SeriesGroupingRule("builtin-cn", "中文卷号", """(?<series>.+?)\s*第\s*(?<index>\d+)\s*[卷册]""", true, 110, true),
        SeriesGroupingRule("builtin-bracket", "括号卷号", """(?<series>.+?)\s*[\[(（](?<index>\d+)[\])）]""", true, 120, true),
        SeriesGroupingRule("builtin-number", "数字后缀", """(?<series>.+?)\s+(?<index>\d{1,3})""", true, 130, true),
    )

    fun entries(
        books: List<BookshelfBook>,
        customRules: List<SeriesGroupingRule>,
        disabledBuiltInRuleIds: Set<String> = emptySet(),
    ): List<BookshelfEntry> {
        val rules = customRules
            .filter { it.enabled }
            .sortedBy { it.priority } +
            builtInRules.filter { it.enabled && it.id !in disabledBuiltInRuleIds }.sortedBy { it.priority }

        val grouped = books.groupBy { book ->
            // 分组优先级必须保持：手动 > EPUB/Calibre 元数据 > 用户规则 > 内置规则。
            book.manualSeries?.trim()?.takeIf { it.isNotEmpty() }
                ?: book.metadataSeries?.trim()?.takeIf { it.isNotEmpty() }
                ?: rules.firstNotNullOfOrNull { rule -> matchSeries(rule, book.title)?.series }
                ?: SINGLE_PREFIX + book.id
        }

        return grouped.values
            .map { group ->
                val first = group.first()
                val key = first.manualSeries ?: first.metadataSeries ?: groupKeyFromRules(group, rules)
                if (group.size >= 2 && key != null) {
                    BookshelfEntry.Series(
                        BookshelfSeries(
                            id = key,
                            title = key,
                            books = sortSeriesBooks(group, rules),
                            progress = group.map { normalizeProgress(it.totalProgression) }.average(),
                        )
                    )
                } else {
                    BookshelfEntry.SingleBook(first, normalizeProgress(first.totalProgression))
                }
            }
            .sortedWith(compareBy({ entrySortTime(it) }, { entryTitle(it) }))
    }

    fun normalizeProgress(value: Double?): Double {
        val clamped = (value ?: 0.0).coerceIn(0.0, 1.0)
        return if (clamped >= 0.99) 1.0 else clamped
    }

    private fun groupKeyFromRules(group: List<BookshelfBook>, rules: List<SeriesGroupingRule>): String? =
        group.firstNotNullOfOrNull { book -> rules.firstNotNullOfOrNull { rule -> matchSeries(rule, book.title)?.series } }

    private fun sortSeriesBooks(books: List<BookshelfBook>, rules: List<SeriesGroupingRule>): List<BookshelfBook> =
        books.sortedWith(
            compareBy<BookshelfBook> { it.manualSeriesIndex ?: it.metadataSeriesIndex ?: rules.firstNotNullOfOrNull { rule -> matchSeries(rule, it.title)?.index } ?: Double.MAX_VALUE }
                .thenBy { it.title }
        )

    private fun matchSeries(rule: SeriesGroupingRule, title: String): RuleMatch? =
        runCatching {
            val match = Regex(rule.pattern).find(title) ?: return null
            val series = match.groups["series"]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val index = match.groups["index"]?.value?.toDoubleOrNull()
            RuleMatch(series, index)
        }.getOrNull()

    private fun entrySortTime(entry: BookshelfEntry): Long =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.books.maxOf { it.lastOpenedAt ?: it.updatedAt }
            is BookshelfEntry.SingleBook -> entry.book.lastOpenedAt ?: entry.book.updatedAt
        } * -1

    private fun entryTitle(entry: BookshelfEntry): String =
        when (entry) {
            is BookshelfEntry.Series -> entry.series.title
            is BookshelfEntry.SingleBook -> entry.book.title
        }

    private data class RuleMatch(val series: String, val index: Double?)

    private const val SINGLE_PREFIX = "single:"
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGroupingTest --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf app/src/test/java/io/github/luoyuxiaoxiao/easyreader/domain/bookshelf
git commit -m "feat: add bookshelf grouping domain"
```

---

### Task 2: Persist Series Grouping Rules

**Files:**
- Create: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStore.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStoreTest.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt`

- [ ] **Step 1: Write failing serialization tests**

Create `SeriesGroupingRuleStoreTest.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.data.settings

import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesGroupingRuleStoreTest {
    @Test
    fun encodesAndDecodesSettings() {
        val settings = SeriesGroupingRuleSettings(
            customRules = listOf(
                SeriesGroupingRule(
                    id = "custom-1",
                    name = "Fate",
                    pattern = """(?<series>Fate).+?(?<index>\d+)""",
                    enabled = true,
                    priority = 0,
                    builtIn = false,
                )
            ),
            disabledBuiltInRuleIds = setOf("builtin-number"),
        )

        val encoded = SeriesGroupingRuleSerializer.encode(settings)
        val decoded = SeriesGroupingRuleSerializer.decode(encoded)

        assertEquals(settings, decoded)
    }

    @Test
    fun invalidStoredJsonFallsBackToDefaultSettings() {
        val decoded = SeriesGroupingRuleSerializer.decode("{bad json")

        assertTrue(decoded.customRules.isEmpty())
        assertTrue(decoded.disabledBuiltInRuleIds.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.data.settings.SeriesGroupingRuleStoreTest --no-daemon
```

Expected: FAIL with unresolved `SeriesGroupingRuleSerializer` or `SeriesGroupingRuleSettings`.

- [ ] **Step 3: Implement rule store**

Create `SeriesGroupingRuleStore.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRuleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.seriesGroupingRuleDataStore by preferencesDataStore(name = "series_grouping_rules")

class SeriesGroupingRuleStore(context: Context) {
    private val dataStore = context.applicationContext.seriesGroupingRuleDataStore

    val settings: Flow<SeriesGroupingRuleSettings> =
        dataStore.data.map { preferences ->
            SeriesGroupingRuleSerializer.decode(preferences[SETTINGS_JSON])
        }

    suspend fun updateSettings(settings: SeriesGroupingRuleSettings) {
        dataStore.edit { preferences ->
            preferences[SETTINGS_JSON] = SeriesGroupingRuleSerializer.encode(settings)
        }
    }

    suspend fun updateCustomRules(rules: List<SeriesGroupingRule>) {
        val current = settings.first()
        updateSettings(current.copy(customRules = rules))
    }

    suspend fun setBuiltInRuleEnabled(ruleId: String, enabled: Boolean) {
        val current = settings.first()
        val disabled = if (enabled) {
            current.disabledBuiltInRuleIds - ruleId
        } else {
            current.disabledBuiltInRuleIds + ruleId
        }
        updateSettings(current.copy(disabledBuiltInRuleIds = disabled))
    }

    private companion object {
        val SETTINGS_JSON = stringPreferencesKey("settings_json")
    }
}

object SeriesGroupingRuleSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(settings: SeriesGroupingRuleSettings): String =
        json.encodeToString(settings.toStored())

    fun decode(value: String?): SeriesGroupingRuleSettings =
        if (value.isNullOrBlank()) {
            SeriesGroupingRuleSettings()
        } else {
            runCatching { json.decodeFromString<StoredSeriesGroupingRuleSettings>(value).toDomain() }
                .getOrDefault(SeriesGroupingRuleSettings())
        }
}

@Serializable
private data class StoredSeriesGroupingRuleSettings(
    val customRules: List<StoredSeriesGroupingRule> = emptyList(),
    val disabledBuiltInRuleIds: Set<String> = emptySet(),
)

@Serializable
private data class StoredSeriesGroupingRule(
    val id: String,
    val name: String,
    val pattern: String,
    val enabled: Boolean,
    val priority: Int,
    val builtIn: Boolean,
)

private fun SeriesGroupingRuleSettings.toStored(): StoredSeriesGroupingRuleSettings =
    StoredSeriesGroupingRuleSettings(
        customRules = customRules.map { it.toStored() },
        disabledBuiltInRuleIds = disabledBuiltInRuleIds,
    )

private fun StoredSeriesGroupingRuleSettings.toDomain(): SeriesGroupingRuleSettings =
    SeriesGroupingRuleSettings(
        customRules = customRules.map { it.toDomain() },
        disabledBuiltInRuleIds = disabledBuiltInRuleIds,
    )

private fun SeriesGroupingRule.toStored(): StoredSeriesGroupingRule =
    StoredSeriesGroupingRule(id, name, pattern, enabled, priority, builtIn)

private fun StoredSeriesGroupingRule.toDomain(): SeriesGroupingRule =
    SeriesGroupingRule(id, name, pattern, enabled, priority, builtIn)
```

- [ ] **Step 4: Wire store into AppContainer**

Modify `AppContainer.kt`:

```kotlin
val seriesGroupingRuleStore: SeriesGroupingRuleStore by lazy {
    SeriesGroupingRuleStore(applicationContext)
}
```

Add import:

```kotlin
import io.github.luoyuxiaoxiao.easyreader.data.settings.SeriesGroupingRuleStore
```

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.data.settings.SeriesGroupingRuleStoreTest --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStore.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt app/src/test/java/io/github/luoyuxiaoxiao/easyreader/data/settings/SeriesGroupingRuleStoreTest.kt
git commit -m "feat: persist series grouping rules"
```

---

### Task 3: Room Schema for Series and Bookshelf Progress

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/book/BookModels.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookEntities.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookDao.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local/BookRepository.kt`
- Modify all existing `Book(...)` and `BookEntity(...)` call sites that fail compilation after new fields are added.
- Modify: `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt`
- Generated: `app/schemas/io.github.luoyuxiaoxiao.easyreader.data.local.AppDatabase/2.json`

- [ ] **Step 1: Write failing database test**

Extend `AppDatabaseTest.kt` with:

```kotlin
@Test
fun observesBookshelfBooksWithManualSeriesAndProgress() = runBlocking {
    val book = BookEntity(
        id = "book-series-1",
        title = "Series Vol.01",
        author = "Author",
        filePath = "/books/book-series-1/book.epub",
        sha256 = "hash-series-1",
        coverPath = "/books/book-series-1/cover.jpg",
        metadataSeries = "Series",
        metadataSeriesIndex = 1.0,
        manualSeries = "Manual Series",
        manualSeriesIndex = 2.0,
        createdAt = 100L,
        updatedAt = 200L,
        lastOpenedAt = null,
    )
    val progress = ReadingProgressEntity(
        bookId = book.id,
        locatorJson = """{"href":"chapter.xhtml"}""",
        readingOrderIndex = 0,
        totalProgression = 0.5,
        chapterProgression = 0.5,
        updatedAt = 300L,
    )

    database.bookDao().upsert(book)
    database.readingProgressDao().upsert(progress)

    val snapshot = database.bookDao().observeBookshelfBooks().first().single()
    assertEquals("Manual Series", snapshot.manualSeries)
    assertEquals(0.5, snapshot.totalProgression!!, 0.0001)
}
```

Update existing `BookEntity(...)` construction in the same file by adding:

```kotlin
metadataSeries = null,
metadataSeriesIndex = null,
manualSeries = null,
manualSeriesIndex = null,
```

- [ ] **Step 2: Run instrumentation compile to verify it fails**

Run:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

Expected: FAIL with unresolved `observeBookshelfBooks` or missing `BookEntity` fields.

- [ ] **Step 3: Add fields and joined snapshot**

Modify `BookModels.kt`:

```kotlin
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val sha256: String,
    val coverPath: String?,
    val metadataSeries: String?,
    val metadataSeriesIndex: Double?,
    val manualSeries: String?,
    val manualSeriesIndex: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
)
```

Add:

```kotlin
data class BookshelfBookSnapshot(
    val book: Book,
    val totalProgression: Double?,
)
```

Modify `BookEntity`:

```kotlin
val metadataSeries: String?,
val metadataSeriesIndex: Double?,
val manualSeries: String?,
val manualSeriesIndex: Double?,
```

Create a Room projection in `BookEntities.kt`:

```kotlin
data class BookshelfBookProjection(
    val id: String,
    val title: String,
    val author: String?,
    val filePath: String,
    val sha256: String,
    val coverPath: String?,
    val metadataSeries: String?,
    val metadataSeriesIndex: Double?,
    val manualSeries: String?,
    val manualSeriesIndex: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long?,
    val totalProgression: Double?,
)
```

- [ ] **Step 4: Add DAO queries and migration**

Modify `BookDao.kt`:

```kotlin
@Query(
    """
    SELECT books.*, reading_progress.totalProgression AS totalProgression
    FROM books
    LEFT JOIN reading_progress ON reading_progress.bookId = books.id
    ORDER BY COALESCE(books.lastOpenedAt, books.updatedAt) DESC
    """
)
fun observeBookshelfBooks(): Flow<List<BookshelfBookProjection>>

@Query("UPDATE books SET manualSeries = :series, manualSeriesIndex = :seriesIndex, updatedAt = :updatedAt WHERE id IN (:bookIds)")
suspend fun updateManualSeries(bookIds: List<String>, series: String?, seriesIndex: Double?, updatedAt: Long)
```

Modify `AppDatabase.kt`:

```kotlin
@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
        ShortcutEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun shortcutDao(): ShortcutDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN metadataSeries TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN metadataSeriesIndex REAL")
                db.execSQL("ALTER TABLE books ADD COLUMN manualSeries TEXT")
                db.execSQL("ALTER TABLE books ADD COLUMN manualSeriesIndex REAL")
            }
        }
    }
}
```

Modify `AppContainer.kt` database builder:

```kotlin
.addMigrations(AppDatabase.MIGRATION_1_2)
.fallbackToDestructiveMigration(dropAllTables = true)
```

- [ ] **Step 5: Map repository snapshots**

Modify `BookRepository.kt`:

```kotlin
fun observeBookshelfBooks(): Flow<List<BookshelfBookSnapshot>> =
    bookDao.observeBookshelfBooks().map { rows ->
        rows.map { row ->
            BookshelfBookSnapshot(
                book = row.toDomainBook(),
                totalProgression = row.totalProgression,
            )
        }
    }

suspend fun updateManualSeries(bookIds: List<String>, series: String?, seriesIndex: Double?) {
    if (bookIds.isEmpty()) return
    bookDao.updateManualSeries(bookIds, series, seriesIndex, System.currentTimeMillis())
}
```

Add `toDomainBook()` for `BookshelfBookProjection` and update existing `toDomain()` / `toEntity()` mappings with the new fields. Then run a project compile and update any failing constructor call by passing:

```kotlin
metadataSeries = null,
metadataSeriesIndex = null,
manualSeries = null,
manualSeriesIndex = null,
```

- [ ] **Step 6: Run instrumentation compile and generate schema**

Run:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL` and schema `2.json` generated.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/book/BookModels.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/data/local app/src/main/java/io/github/luoyuxiaoxiao/easyreader/core/di/AppContainer.kt app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/data/local/AppDatabaseTest.kt app/schemas/io.github.luoyuxiaoxiao.easyreader.data.local.AppDatabase
git commit -m "feat: store bookshelf series metadata"
```

---

### Task 4: EPUB Cover and Series Import

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/EpubImportService.kt`
- Modify: `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/fixtures/MinimalEpubFixture.kt`
- Modify: `app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/importer/EpubImportServiceTest.kt`

- [ ] **Step 1: Extend fixture with cover and Calibre series**

Add fixture options:

```kotlin
data class MinimalEpubOptions(
    val title: String = "Minimal EPUB",
    val author: String = "EasyReader",
    val includeCover: Boolean = false,
    val calibreSeries: String? = null,
    val calibreSeriesIndex: Double? = null,
)

fun writeTo(file: File, options: MinimalEpubOptions = MinimalEpubOptions()) {
    // 核心测试夹具必须能开关封面和系列元数据，保证导入解析逻辑可重复验证。
}
```

When `includeCover = true`, add this OPF manifest item:

```xml
<item id="cover-img" href="images/cover.png" media-type="image/png" properties="cover-image"/>
```

Add entry `OEBPS/images/cover.png` from a small hardcoded 1x1 PNG byte array encoded in Kotlin:

```kotlin
private val tinyPng = java.util.Base64.getDecoder().decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
)
```

- [ ] **Step 2: Write failing import tests**

Add to `EpubImportServiceTest.kt`:

```kotlin
@Test
fun importsCoverImageAndStoresCoverPath() = runBlocking {
    val epub = writeFixture("cover.epub", MinimalEpubOptions(includeCover = true))

    service.importUris(listOf(Uri.fromFile(epub)))

    val book = repository.observeBooks().first().single()
    val coverPath = requireNotNull(book.coverPath)
    assertTrue(File(coverPath).isFile)
    assertTrue(File(coverPath).length() > 0)
}

@Test
fun importsCalibreSeriesMetadata() = runBlocking {
    val epub = writeFixture(
        "series.epub",
        MinimalEpubOptions(calibreSeries = "Fate stay night", calibreSeriesIndex = 1.0),
    )

    service.importUris(listOf(Uri.fromFile(epub)))

    val book = repository.observeBooks().first().single()
    assertEquals("Fate stay night", book.metadataSeries)
    assertEquals(1.0, book.metadataSeriesIndex!!, 0.0001)
}
```

Change helper:

```kotlin
private fun writeFixture(name: String, options: MinimalEpubOptions = MinimalEpubOptions()): File =
    File(context.cacheDir, name).also { MinimalEpubFixture.writeTo(it, options) }
```

- [ ] **Step 3: Run test to verify it fails**

Run:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

Expected: FAIL because import service does not write `coverPath` or series fields.

- [ ] **Step 4: Implement OPF series and cover parsing**

Modify parsed metadata:

```kotlin
private data class ParsedEpub(
    val title: String,
    val author: String?,
    val chapters: List<ParsedChapter>,
    val cover: ParsedCover?,
    val series: String?,
    val seriesIndex: Double?,
)

private data class ParsedCover(
    val zipPath: String,
    val extension: String,
)
```

Inside `EpubMetadataParser.parse`, build manifest items with id, href, media type, properties:

```kotlin
private data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: String,
)
```

Extract:

```kotlin
val series = opf.calibreMeta("series") ?: opf.epub3SeriesName()
val seriesIndex = opf.calibreMeta("series_index")?.toDoubleOrNull() ?: opf.epub3SeriesIndex()
val cover = resolveCover(opfPath, manifest, opf)
```

Add helpers:

```kotlin
private fun org.w3c.dom.Document.calibreMeta(name: String): String? =
    elements("meta")
        .firstOrNull { it.attribute("name") == "calibre:$name" }
        ?.attribute("content")
        ?.takeIf { it.isNotBlank() }

private fun org.w3c.dom.Document.epub3SeriesName(): String? {
    val collection = elements("meta").firstOrNull { meta ->
        meta.attribute("property") == "belongs-to-collection" &&
            elements("meta").any { refine ->
                refine.attribute("refines") == "#${meta.attribute("id")}" &&
                    refine.attribute("property") == "collection-type" &&
                    refine.textContent.trim() == "series"
            }
    }
    return collection?.textContent?.trim()?.takeIf { it.isNotBlank() }
}

private fun org.w3c.dom.Document.epub3SeriesIndex(): Double? {
    val collectionId = elements("meta").firstOrNull { meta ->
        meta.attribute("property") == "belongs-to-collection"
    }?.attribute("id")?.takeIf { it.isNotBlank() } ?: return null
    return elements("meta")
        .firstOrNull { it.attribute("refines") == "#$collectionId" && it.attribute("property") == "group-position" }
        ?.textContent
        ?.trim()
        ?.toDoubleOrNull()
}

private fun resolveCover(
    opfPath: String,
    manifest: Map<String, ManifestItem>,
    document: org.w3c.dom.Document,
): ParsedCover? {
    val coverItem = manifest.values.firstOrNull { item ->
        item.properties.split(' ').any { it == "cover-image" }
    } ?: document.elements("meta")
        .firstOrNull { it.attribute("name") == "cover" }
        ?.attribute("content")
        ?.let { manifest[it] }
        ?: manifest.values.firstOrNull { it.mediaType.startsWith("image/") && it.href.contains("cover", ignoreCase = true) }

    val item = coverItem ?: return null
    val path = opfPath.substringBeforeLast('/', "").resolveZipPath(item.href)
    val extension = path.substringAfterLast('.', "jpg").lowercase()
    return ParsedCover(path, extension)
}
```

When saving a new book:

```kotlin
val coverPath = metadata.cover?.let { cover ->
    ZipFile(epubFile).use { zip ->
        val bytes = zip.getInputStream(zip.getEntry(cover.zipPath)).use { it.readBytes() }
        saveCoverThumbnail(bytes, bookDirectory)
    }
}
```

Add thumbnail helper in `EpubImportService.kt`:

```kotlin
private fun saveCoverThumbnail(bytes: ByteArray, bookDirectory: File): String? {
    val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val maxEdge = maxOf(original.width, original.height)
    val bitmap = if (maxEdge > COVER_MAX_LONG_EDGE) {
        val scale = COVER_MAX_LONG_EDGE.toFloat() / maxEdge.toFloat()
        android.graphics.Bitmap.createScaledBitmap(
            original,
            (original.width * scale).toInt().coerceAtLeast(1),
            (original.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        original
    }
    val target = File(bookDirectory, "cover.jpg")
    target.outputStream().use { output ->
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, output)
    }
    if (bitmap !== original) bitmap.recycle()
    original.recycle()
    return target.absolutePath
}

private const val COVER_MAX_LONG_EDGE = 512
private const val COVER_JPEG_QUALITY = 85
```

Set book fields:

```kotlin
coverPath = coverPath,
metadataSeries = metadata.series,
metadataSeriesIndex = metadata.seriesIndex,
manualSeries = null,
manualSeriesIndex = null,
```

- [ ] **Step 5: Run tests**

Run:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/domain/importer/EpubImportService.kt app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/fixtures/MinimalEpubFixture.kt app/src/androidTest/java/io/github/luoyuxiaoxiao/easyreader/importer/EpubImportServiceTest.kt
git commit -m "feat: import epub cover and series metadata"
```

---

### Task 5: Bookshelf ViewModel State

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt`
- Create: `app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel-free state test**

Extract a pure reducer in `BookshelfViewModel.kt`:

```kotlin
internal fun buildBookshelfEntries(
    snapshots: List<BookshelfBookSnapshot>,
    customRules: List<SeriesGroupingRule>,
    disabledBuiltInRuleIds: Set<String> = emptySet(),
): List<BookshelfEntry>
```

Test it in `BookshelfViewModelTest.kt`:

```kotlin
package io.github.luoyuxiaoxiao.easyreader.ui.bookshelf

import io.github.luoyuxiaoxiao.easyreader.domain.book.Book
import io.github.luoyuxiaoxiao.easyreader.domain.book.BookshelfBookSnapshot
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfViewModelTest {
    @Test
    fun buildsSeriesEntriesFromRepositorySnapshots() {
        val entries = buildBookshelfEntries(
            snapshots = listOf(
                snapshot("1", "Fate Vol.01", 1.0),
                snapshot("2", "Fate Vol.02", 0.5),
            ),
            customRules = emptyList(),
        )

        val series = entries.single() as BookshelfEntry.Series
        assertEquals("Fate", series.series.title)
        assertEquals(0.75, series.series.progress, 0.0001)
    }

    private fun snapshot(id: String, title: String, progress: Double) =
        BookshelfBookSnapshot(
            book = Book(
                id = id,
                title = title,
                author = null,
                filePath = "/$id.epub",
                sha256 = id,
                coverPath = null,
                metadataSeries = null,
                metadataSeriesIndex = null,
                manualSeries = null,
                manualSeriesIndex = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastOpenedAt = null,
            ),
            totalProgression = progress,
        )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfViewModelTest --no-daemon
```

Expected: FAIL with unresolved `buildBookshelfEntries`.

- [ ] **Step 3: Update UI state and reducer**

Add imports:

```kotlin
import io.github.luoyuxiaoxiao.easyreader.domain.book.BookshelfBookSnapshot
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfBook
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfEntry
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGrouping
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
```

Modify `BookshelfUiState`:

```kotlin
data class BookshelfUiState(
    val entries: List<BookshelfEntry> = emptyList(),
    val booksById: Map<String, Book> = emptyMap(),
    val selectedBookIds: Set<String> = emptySet(),
    val openedSeriesId: String? = null,
    val customRules: List<SeriesGroupingRule> = emptyList(),
    val disabledBuiltInRuleIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val message: String? = null,
) {
    val isSelecting: Boolean = selectedBookIds.isNotEmpty()
}
```

Add reducer:

```kotlin
internal fun buildBookshelfEntries(
    snapshots: List<BookshelfBookSnapshot>,
    customRules: List<SeriesGroupingRule>,
    disabledBuiltInRuleIds: Set<String> = emptySet(),
): List<BookshelfEntry> =
    BookshelfGrouping.entries(
        books = snapshots.map { snapshot ->
            val book = snapshot.book
            BookshelfBook(
                id = book.id,
                title = book.title,
                author = book.author,
                coverPath = book.coverPath,
                metadataSeries = book.metadataSeries,
                metadataSeriesIndex = book.metadataSeriesIndex,
                manualSeries = book.manualSeries,
                manualSeriesIndex = book.manualSeriesIndex,
                lastOpenedAt = book.lastOpenedAt,
                updatedAt = book.updatedAt,
                totalProgression = snapshot.totalProgression,
            )
        },
        customRules = customRules,
        disabledBuiltInRuleIds = disabledBuiltInRuleIds,
    )
```

- [ ] **Step 4: Combine repository and rules store**

Change `BookshelfViewModel` constructor:

```kotlin
class BookshelfViewModel(
    private val bookRepository: BookRepository,
    private val epubImportService: EpubImportService,
    private val shortcutInstaller: ShortcutInstaller,
    private val seriesGroupingRuleStore: SeriesGroupingRuleStore,
) : ViewModel()
```

In `init`, combine flows:

```kotlin
viewModelScope.launch {
    combine(
        bookRepository.observeBookshelfBooks(),
        seriesGroupingRuleStore.settings,
    ) { snapshots, settings ->
        snapshots to settings
    }.collect { (snapshots, settings) ->
        _uiState.update { state ->
            state.copy(
                entries = buildBookshelfEntries(
                    snapshots = snapshots,
                    customRules = settings.customRules,
                    disabledBuiltInRuleIds = settings.disabledBuiltInRuleIds,
                ),
                booksById = snapshots.associate { it.book.id to it.book },
                customRules = settings.customRules,
                disabledBuiltInRuleIds = settings.disabledBuiltInRuleIds,
            )
        }
    }
}
```

Add import:

```kotlin
import io.github.luoyuxiaoxiao.easyreader.data.settings.SeriesGroupingRuleStore
import kotlinx.coroutines.flow.combine
```

Update `MainActivity.kt` factory call with `seriesGroupingRuleStore = appContainer.seriesGroupingRuleStore`.

- [ ] **Step 5: Preserve import/open/selection behavior**

Replace previous flat book-list state reads with `state.booksById` and `state.entries`.

```kotlin
internal fun BookshelfUiState.allBookshelfBooks(): List<BookshelfBook> =
    entries.flatMap { entry ->
        when (entry) {
            is BookshelfEntry.Series -> entry.series.books
            is BookshelfEntry.SingleBook -> listOf(entry.book)
        }
    }
```

Use `booksById` inside `requestShortcutsForSelection()` so `ShortcutInstaller` still receives full domain `Book` values:

```kotlin
val selectedBooks = state.selectedBookIds.mapNotNull { state.booksById[it] }
```

Use `allBookshelfBooks()` only for UI dialogs and selection counts.

- [ ] **Step 6: Run tests**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest --tests io.github.luoyuxiaoxiao.easyreader.ui.bookshelf.BookshelfViewModelTest --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/MainActivity.kt app/src/test/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModelTest.kt
git commit -m "feat: build bookshelf grid state"
```

---

### Task 6: Compose Grid and Series Detail UI

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`

- [ ] **Step 1: Replace LazyColumn with grid skeleton**

In `BookshelfScreen.kt`, add imports:

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfBook
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfEntry
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfSeries
```

Add grid:

```kotlin
@Composable
private fun BookshelfGrid(
    entries: List<BookshelfEntry>,
    selectedBookIds: Set<String>,
    selecting: Boolean,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { entryKey(it) }) { entry ->
            when (entry) {
                is BookshelfEntry.Series -> SeriesStackItem(entry.series, onClick = { onSeriesClick(entry.series.id) })
                is BookshelfEntry.SingleBook -> BookGridItem(
                    book = entry.book,
                    progress = entry.progress,
                    selected = entry.book.id in selectedBookIds,
                    selecting = selecting,
                    onClick = { onBookClick(entry.book.id) },
                    onLongClick = { onBookLongClick(entry.book.id) },
                )
            }
        }
    }
}

private fun entryKey(entry: BookshelfEntry): String =
    when (entry) {
        is BookshelfEntry.Series -> "series:${entry.series.id}"
        is BookshelfEntry.SingleBook -> "book:${entry.book.id}"
    }
```

- [ ] **Step 2: Implement cover item**

Add:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookGridItem(
    book: BookshelfBook,
    progress: Double,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        BookCoverBox(book = book, modifier = Modifier.fillMaxWidth().aspectRatio(0.68f))
        LinearProgressIndicator(
            progress = { progress.toFloat() },
            color = BookshelfProgressGreen,
            modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 4.dp),
        )
        Text(
            text = book.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = null)
        }
    }
}
```

Implement `BookCoverBox`:

```kotlin
@Composable
private fun BookCoverBox(book: BookshelfBook, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = book.coverPath) {
        value = withContext(Dispatchers.IO) {
            book.coverPath?.let { path -> BitmapFactory.decodeFile(path) }
        }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = book.title.take(12), textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
        }
    }
}
```

Add a stable progress color near the composables:

```kotlin
private val BookshelfProgressGreen = Color(0xFF18A558)
```

- [ ] **Step 3: Implement series stack**

Add:

```kotlin
@Composable
private fun SeriesStackItem(series: BookshelfSeries, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.82f)) {
            series.books.take(4).reversed().forEachIndexed { index, book ->
                BookCoverBox(
                    book = book,
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .aspectRatio(0.68f)
                        .align(Alignment.Center)
                        .padding(start = (index * 5).dp),
                )
            }
            Text(
                text = "${series.books.size} 本",
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.scrim).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        LinearProgressIndicator(
            progress = { series.progress.toFloat() },
            color = BookshelfProgressGreen,
            modifier = Modifier.fillMaxWidth().height(5.dp),
        )
        Text(text = series.title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
```

- [ ] **Step 4: Add series detail state**

In `BookshelfContent`, compute:

```kotlin
val openedSeries = state.entries
    .filterIsInstance<BookshelfEntry.Series>()
    .firstOrNull { it.series.id == state.openedSeriesId }
```

When `openedSeries != null`, render top bar title as `openedSeries.series.title` and grid of `BookGridItem` for `openedSeries.series.books`.

Add ViewModel functions:

```kotlin
fun openSeries(seriesId: String) {
    _uiState.update { it.copy(openedSeriesId = seriesId, selectedBookIds = emptySet()) }
}

fun closeSeries() {
    _uiState.update { it.copy(openedSeriesId = null, selectedBookIds = emptySet()) }
}
```

- [ ] **Step 5: Compile UI**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt
git commit -m "feat: render bookshelf grid"
```

---

### Task 7: Manual Series and Rule Editor Entry

**Files:**
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt`
- Modify: `app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt`

- [ ] **Step 1: Add ViewModel actions**

Add import:

```kotlin
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.RuleValidationResult
```

Add:

```kotlin
fun assignSelectedToSeries(series: String) {
    val selected = uiState.value.selectedBookIds
    if (selected.isEmpty()) {
        showMessage("请选择书籍")
        return
    }
    val trimmed = series.trim()
    if (trimmed.isEmpty()) {
        showMessage("系列名不能为空")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        bookRepository.updateManualSeries(selected.toList(), trimmed, null)
        _uiState.update { it.copy(selectedBookIds = emptySet(), message = "已加入系列：$trimmed") }
    }
}

fun removeSelectedFromSeries() {
    val selected = uiState.value.selectedBookIds
    if (selected.isEmpty()) {
        showMessage("请选择书籍")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        bookRepository.updateManualSeries(selected.toList(), null, null)
        _uiState.update { it.copy(selectedBookIds = emptySet(), message = "已移出手动系列") }
    }
}

fun addCustomRule(rule: SeriesGroupingRule) {
    if (SeriesGroupingRule.validate(rule.pattern) !is RuleValidationResult.Valid) {
        showMessage("正则需要包含 series 捕获组")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        seriesGroupingRuleStore.updateCustomRules(uiState.value.customRules + rule)
    }
}

fun setBuiltInRuleEnabled(ruleId: String, enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
        seriesGroupingRuleStore.setBuiltInRuleEnabled(ruleId, enabled)
    }
}

fun setCustomRuleEnabled(ruleId: String, enabled: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = uiState.value.customRules.map { rule ->
            if (rule.id == ruleId) rule.copy(enabled = enabled) else rule
        }
        seriesGroupingRuleStore.updateCustomRules(updated)
    }
}
```

- [ ] **Step 2: Add selection actions in top bar**

Add imports:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.BookshelfGrouping
import io.github.luoyuxiaoxiao.easyreader.domain.bookshelf.SeriesGroupingRule
```

Extend `BookshelfContent` parameters:

```kotlin
onAssignSeries: (String) -> Unit,
onRemoveFromSeries: () -> Unit,
onAddRule: (SeriesGroupingRule) -> Unit,
onSetBuiltInRuleEnabled: (String, Boolean) -> Unit,
onSetCustomRuleEnabled: (String, Boolean) -> Unit,
```

Add local dialog state in `BookshelfContent`:

```kotlin
var showSeriesDialog by remember { mutableStateOf(false) }
var seriesName by remember { mutableStateOf("") }
var showRuleDialog by remember { mutableStateOf(false) }
var ruleName by remember { mutableStateOf("") }
var rulePattern by remember { mutableStateOf("") }
```

When `state.isSelecting`, add text buttons:

```kotlin
TextButton(onClick = { showSeriesDialog = true }) { Text("加入系列") }
TextButton(onClick = onRemoveFromSeries) { Text("移出系列") }
TextButton(onClick = onRequestShortcuts) { Text("快捷方式") }
TextButton(onClick = onClearSelection) { Text("取消") }
```

Add simple dialog:

```kotlin
if (showSeriesDialog) {
    AlertDialog(
        onDismissRequest = { showSeriesDialog = false },
        title = { Text("加入系列") },
        text = {
            TextField(value = seriesName, onValueChange = { seriesName = it }, label = { Text("系列名") })
        },
        confirmButton = {
            TextButton(onClick = {
                onAssignSeries(seriesName)
                showSeriesDialog = false
            }) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = { showSeriesDialog = false }) { Text("取消") }
        },
    )
}
```

- [ ] **Step 3: Add rule editor entry**

In non-selecting top bar actions, keep import and add:

```kotlin
TextButton(onClick = { showRuleDialog = true }) {
    Text("归组规则")
}
```

Add callbacks from `BookshelfScreen` into `BookshelfContent`:

```kotlin
onAssignSeries = viewModel::assignSelectedToSeries,
onRemoveFromSeries = viewModel::removeSelectedFromSeries,
onAddRule = viewModel::addCustomRule,
onSetBuiltInRuleEnabled = viewModel::setBuiltInRuleEnabled,
onSetCustomRuleEnabled = viewModel::setCustomRuleEnabled,
```

Add a preview helper near the dialog code:

```kotlin
@Composable
private fun RulePreview(pattern: String, books: List<BookshelfBook>) {
    val preview = remember(pattern, books) {
        runCatching {
            val rule = SeriesGroupingRule("preview", "预览", pattern, true, 0, false)
            BookshelfGrouping.entries(
                books = books,
                customRules = listOf(rule),
                disabledBuiltInRuleIds = BookshelfGrouping.builtInRules.map { it.id }.toSet(),
            )
                .filterIsInstance<BookshelfEntry.Series>()
                .joinToString { "${it.series.title} (${it.series.books.size})" }
        }.getOrDefault("")
    }
    Text(if (preview.isBlank()) "暂无可折叠系列" else preview)
}
```

Add minimal rule dialog:

```kotlin
if (showRuleDialog) {
    AlertDialog(
        onDismissRequest = { showRuleDialog = false },
        title = { Text("系列归组规则") },
        text = {
            Column {
                Text("内置规则")
                BookshelfGrouping.builtInRules.forEach { rule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rule.id !in state.disabledBuiltInRuleIds,
                            onCheckedChange = { checked -> onSetBuiltInRuleEnabled(rule.id, checked) },
                        )
                        Text(rule.name)
                    }
                }
                Text("自定义规则")
                state.customRules.forEach { rule ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rule.enabled,
                            onCheckedChange = { checked -> onSetCustomRuleEnabled(rule.id, checked) },
                        )
                        Text(rule.name)
                    }
                }
                TextField(value = ruleName, onValueChange = { ruleName = it }, label = { Text("名称") })
                TextField(value = rulePattern, onValueChange = { rulePattern = it }, label = { Text("正则") })
                Text("需要命名捕获组：(?<series>...)，可选 (?<index>...)")
                RulePreview(rulePattern, state.allBookshelfBooks())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAddRule(
                    SeriesGroupingRule(
                        id = "custom-${System.currentTimeMillis()}",
                        name = ruleName.ifBlank { "自定义规则" },
                        pattern = rulePattern,
                        enabled = true,
                        priority = state.customRules.size,
                        builtIn = false,
                    )
                )
                showRuleDialog = false
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = { showRuleDialog = false }) { Text("取消") }
        },
    )
}
```

- [ ] **Step 4: Compile**

Run:

```bash
timeout 60s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfViewModel.kt app/src/main/java/io/github/luoyuxiaoxiao/easyreader/ui/bookshelf/BookshelfScreen.kt
git commit -m "feat: add bookshelf series controls"
```

---

### Task 8: Final Verification and Manual Regression

**Files:**
- Create: `docs/superpowers/progress/2026-06-22-easyreader-bookshelf-ui-handoff.md`

- [ ] **Step 1: Run full unit and debug build**

Run:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run instrumentation compile**

Run:

```bash
timeout 300s env GRADLE_USER_HOME=/tmp/easyreader-gradle-home JAVA_HOME=/usr/lib/jvm/java-21-openjdk ANDROID_HOME=/home/luoyu/Android/Sdk ANDROID_SDK_ROOT=/home/luoyu/Android/Sdk ./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual app regression**

Install and test on device when available:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected:

- Import EPUBs with cover images.
- Home bookshelf shows 3-column grid.
- Books with matching series show as one stacked item with `N 本`.
- Stack item progress equals average book progress.
- Tap series opens series detail grid.
- Long press a book enters selection mode.
- “加入系列” assigns manual series and overrides automatic grouping.
- “移出系列” removes manual grouping and automatic grouping resumes.
- Invalid custom regex shows error and does not crash.
- Reader still opens from a single book and from series detail.

- [ ] **Step 4: Write handoff**

Create `docs/superpowers/progress/2026-06-22-easyreader-bookshelf-ui-handoff.md`:

```markdown
# EasyReader 书柜 UI 交接

## 完成内容

- 书柜首页改为 3 列网格。
- 系列以堆叠封面显示。
- 系列内页显示单本书网格。
- 进度条使用 `ReadingProgress.totalProgression`。
- EPUB 导入提取封面和 series 元数据。
- 支持用户自定义正则归组规则。
- 支持手动加入和移出系列。

## 验证

- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:compileDebugAndroidTestKotlin`

## 手动回归重点

- 导入封面显示。
- 系列堆叠显示。
- 手动系列覆盖自动规则。
- 自定义正则错误提示。
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/progress/2026-06-22-easyreader-bookshelf-ui-handoff.md
git commit -m "docs: record bookshelf ui handoff"
```

---

## Self-Review Checklist

- Spec coverage:
  - PV-style 3-column series stack: Task 6.
  - Series detail grid: Task 6.
  - Green progress bars backed by saved total progress: Tasks 1, 3, 5, 6.
  - Series progress as average of single-book progress: Task 1.
  - EPUB/Calibre series metadata: Task 4.
  - Cover extraction: Task 4.
  - Manual series assignment: Tasks 3 and 7.
  - Custom regex grouping rules and preview: Tasks 1, 2, and 7.
  - Built-in grouping rule enable/disable controls: Tasks 2, 5, and 7.
  - Readest-inspired online metadata exclusion: no implementation task, intentionally out of scope.
- Placeholder scan:
  - No unresolved placeholder wording or undefined placeholder steps.
- Type consistency:
  - `SeriesGroupingRule`, `BookshelfBook`, `BookshelfEntry`, and `BookshelfBookSnapshot` are introduced before use in later tasks.
  - `metadataSeries`, `metadataSeriesIndex`, `manualSeries`, and `manualSeriesIndex` are added consistently across domain, Room, repository, and tests.
