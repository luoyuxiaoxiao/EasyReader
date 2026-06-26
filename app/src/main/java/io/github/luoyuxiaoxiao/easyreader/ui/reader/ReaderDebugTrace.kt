package io.github.luoyuxiaoxiao.easyreader.ui.reader

import android.util.Log
import io.github.luoyuxiaoxiao.easyreader.BuildConfig

internal object ReaderDebugTrace {
    private const val TAG = "EasyReaderTrace"

    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }
}
