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
