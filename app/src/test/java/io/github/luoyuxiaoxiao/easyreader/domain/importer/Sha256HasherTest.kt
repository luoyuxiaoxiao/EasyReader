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
