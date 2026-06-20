package io.github.luoyuxiaoxiao.easyreader.core.result

sealed interface EasyReaderResult<out T> {
    data class Success<T>(val value: T) : EasyReaderResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : EasyReaderResult<Nothing>
}
