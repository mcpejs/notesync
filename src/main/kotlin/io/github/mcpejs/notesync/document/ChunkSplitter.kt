package io.github.mcpejs.notesync.document

import java.security.MessageDigest
import java.util.HexFormat

// BLOB 컬럼의 상한
const val MAX_CHUNK_BYTES = 65_535

private const val LF = '\n'.code.toByte()
private const val CR = '\r'.code.toByte()

// ByteArray의 ==는 내용을 비교하지 않아서 Map 키나 비교에는 hashKey를 쓴다
class PreparedChunk(val hash: ByteArray, val content: ByteArray) {
    val hashKey: String = HexFormat.of().formatHex(hash)
}

fun prepareChunks(content: ByteArray): List<PreparedChunk> =
    splitIntoChunks(content).map { PreparedChunk(MessageDigest.getInstance("SHA-256").digest(it), it) }

fun splitIntoChunks(content: ByteArray): List<ByteArray> =
    splitParagraphs(content).flatMap { cutToMaxSize(it) }

// 구분 문자는 앞 문단에 포함한다. 이어 붙이면 원본이 되어야 한다
private fun splitParagraphs(content: ByteArray): List<ByteArray> {
    var start=0
    var i=0

    val result = mutableListOf<ByteArray>()
    while(i < content.size) {
        var end=i
        var breaks=0
        while(end < content.size && lineBreakLength(content, end) > 0) {
            end+=lineBreakLength(content, end)
            breaks++
        }

        if(breaks >= 2) {
            result.add(content.copyOfRange(start, end))
            start=end
            i=end
        } else {
            i++
        }
    }

    if(start < content.size) {
        result.add(content.copyOfRange(start, content.size))
    }
    return result
}

private fun cutToMaxSize(paragraph: ByteArray): List<ByteArray> {
    val pieces=mutableListOf<ByteArray>()
    var from=0
    while(from < paragraph.size) {
        val to=minOf(from + MAX_CHUNK_BYTES, paragraph.size)
        pieces.add(paragraph.copyOfRange(from, to))
        from=to
    }
    return pieces
}

// index에 줄바꿈이 있으면 그 길이(\n은 1, \r\n은 2), 없으면 0
private fun lineBreakLength(content: ByteArray, index: Int): Int = when {
    content[index] == LF -> 1
    content[index] == CR && index + 1 < content.size && content[index + 1] == LF -> 2
    else -> 0
}
