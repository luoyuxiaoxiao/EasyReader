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
            // 数字片段按数值比较，保证 Book 2 排在 Book 10 前面。
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
            this is Number && other is Number ->
                value.compareTo(other.value).takeIf { it != 0 } ?: width.compareTo(other.width)

            this is Text && other is Text -> value.compareTo(other.value)
            this is Number -> -1
            else -> 1
        }
}
