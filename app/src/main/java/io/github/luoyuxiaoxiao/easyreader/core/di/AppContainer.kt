package io.github.luoyuxiaoxiao.easyreader.core.di

import android.content.Context
import androidx.room.Room
import io.github.luoyuxiaoxiao.easyreader.data.local.AppDatabase
import io.github.luoyuxiaoxiao.easyreader.data.local.BookRepository
import io.github.luoyuxiaoxiao.easyreader.data.settings.ReaderSettingsStore
import io.github.luoyuxiaoxiao.easyreader.domain.importer.EpubImportService
import io.github.luoyuxiaoxiao.easyreader.reader.readium.ReadiumServices
import io.github.luoyuxiaoxiao.easyreader.shortcut.ShortcutInstaller

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "easyreader.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(database)
    }

    val readerSettingsStore: ReaderSettingsStore by lazy {
        ReaderSettingsStore(applicationContext)
    }

    val readiumServices: ReadiumServices by lazy {
        ReadiumServices(applicationContext)
    }

    val epubImportService: EpubImportService by lazy {
        // 导入服务统一写入应用私有目录，避免 SAF 临时授权失效影响后续阅读。
        EpubImportService(
            context = applicationContext,
            contentResolver = applicationContext.contentResolver,
            bookRepository = bookRepository,
        )
    }

    val shortcutInstaller: ShortcutInstaller by lazy {
        ShortcutInstaller(applicationContext, bookRepository)
    }
}
