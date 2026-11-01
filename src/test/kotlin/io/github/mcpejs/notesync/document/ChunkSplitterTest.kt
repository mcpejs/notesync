package io.github.mcpejs.notesync.document

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ChunkSplitterTest {
    private fun split(text: String) = splitIntoChunks(text.toByteArray()).map { String(it) }

    private fun join(chunks: List<ByteArray>) = chunks.fold(ByteArray(0)) { joined, chunk -> joined + chunk }

    @Test
    fun emptyContentHasNoChunks() {
        assertEquals(emptyList<String>(), split(""))
    }

    @Test
    fun textWithoutBlankLineIsOneChunk() {
        assertEquals(listOf("abc"), split("abc"))
        assertEquals(listOf("a\nb"), split("a\nb"))
    }

    @Test
    fun separatorStaysWithPreviousChunk() {
        assertEquals(listOf("a\n\n", "b"), split("a\n\nb"))
        assertEquals(listOf("a\r\n\r\n", "b"), split("a\r\n\r\nb"))
        assertEquals(listOf("a\n\n\n\n", "b"), split("a\n\n\n\nb"))
    }

    @Test
    fun edgesMakeNoEmptyChunk() {
        assertEquals(listOf("a\n\n"), split("a\n\n"))
        assertEquals(listOf("\n\n", "a"), split("\n\na"))
    }

    @Test
    fun paragraphAtLimitIsOneChunk() {
        val chunks = splitIntoChunks(ByteArray(MAX_CHUNK_BYTES))

        assertEquals(listOf(MAX_CHUNK_BYTES), chunks.map { it.size })
    }

    @Test
    fun paragraphOverLimitIsCut() {
        val chunks = splitIntoChunks(ByteArray(MAX_CHUNK_BYTES + 1))

        assertEquals(listOf(MAX_CHUNK_BYTES, 1), chunks.map { it.size })
    }

    // 3바이트 글자의 중간이 끊겨도 바이트를 이어 붙이면 원본이다
    @Test
    fun cutInsideCharacterStillRestores() {
        val content = "가".repeat(30_000).toByteArray()

        assertArrayEquals(content, join(splitIntoChunks(content)))
    }

    @Test
    fun sameParagraphGetsSameHash() {
        val chunks = prepareChunks("a\n\na\n\nb".toByteArray())

        assertEquals(chunks[0].hashKey, chunks[1].hashKey)
        assertEquals(32, chunks[0].hash.size)
        assertEquals(64, chunks[0].hashKey.length)
        assertTrue(chunks[0].hashKey != chunks[2].hashKey)
    }

    @Test
    fun randomContentRestoresAndFitsColumn() {
        val random = Random(42)
        val pieces = listOf("a", "가", "\n", "\r\n", "\n\n", "\r\n\r\n", "x".repeat(70_000))
        repeat(200) {
            val content = List(random.nextInt(40)) { pieces.random(random) }.joinToString("").toByteArray()

            val chunks = splitIntoChunks(content)

            assertArrayEquals(content, join(chunks))
            assertTrue(chunks.all { it.isNotEmpty() && it.size <= MAX_CHUNK_BYTES })
        }
    }
}
