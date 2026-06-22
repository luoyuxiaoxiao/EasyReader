package io.github.luoyuxiaoxiao.easyreader.importer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luoyuxiaoxiao.easyreader.data.local.AppDatabase
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.domain.importer.EpubImportService
import io.github.luoyuxiaoxiao.easyreader.domain.importer.EpubMetadataParser
import io.github.luoyuxiaoxiao.easyreader.fixtures.MinimalEpubFixture
import io.github.luoyuxiaoxiao.easyreader.fixtures.MinimalEpubOptions
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubImportServiceTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: AppDatabase
    private lateinit var repository: BookRepository
    private lateinit var service: EpubImportService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepository(database)
        service = EpubImportService(
            context = context,
            contentResolver = context.contentResolver,
            bookRepository = repository,
            booksDirectory = File(context.filesDir, "test-books"),
        )
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "test-books").deleteRecursively()
    }

    @Test
    fun importsMinimalEpubAndSavesChapters() = runBlocking {
        val epub = writeFixture("minimal.epub")

        val results = service.importUris(listOf(Uri.fromFile(epub)))

        assertEquals(1, results.size)
        assertFalse(results.single().duplicate)
        val book = repository.observeBooks().first().single()
        assertEquals("Minimal EPUB", book.title)
        assertEquals("EasyReader", book.author)
        assertEquals(2, database.chapterDao().findByBookId(book.id).size)
    }

    @Test
    fun skipsDuplicateImportBySha256() = runBlocking {
        val epub = writeFixture("duplicate.epub")

        val first = service.importUris(listOf(Uri.fromFile(epub))).single()
        val second = service.importUris(listOf(Uri.fromFile(epub))).single()

        assertFalse(first.duplicate)
        assertTrue(second.duplicate)
        assertEquals(1, repository.observeBooks().first().size)
    }

    @Test
    fun importsCoverImageAndStoresCoverPath() = runBlocking {
        val epub = writeFixture("cover.epub", MinimalEpubOptions(includeCover = true))
        val parsed = EpubMetadataParser.parse(epub)
        val parsedCover = requireNotNull(parsed.cover)
        val decodedCover = ZipFile(epub).use { zip ->
            val entry = requireNotNull(zip.getEntry(parsedCover.zipPath))
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        requireNotNull(decodedCover)

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

    private fun writeFixture(name: String, options: MinimalEpubOptions = MinimalEpubOptions()): File =
        File(context.cacheDir, name).also { MinimalEpubFixture.writeTo(it, options) }
}
