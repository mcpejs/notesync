package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.user.UserService
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.security.MessageDigest
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class ChunkMappingTest {
    @Autowired lateinit var documentService: DocumentService
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var chunkRepository: ChunkRepository
    @Autowired lateinit var versionChunkRepository: VersionChunkRepository

    private fun sha256(content: ByteArray) = MessageDigest.getInstance("SHA-256").digest(content)

    @Test
    fun largestChunkIsFoundByHashAndKeepsBytes() {
        val user = userService.create("tester", "test-${UUID.randomUUID()}@example.com", "password123")
        val content = ByteArray(MAX_CHUNK_BYTES) { it.toByte() }
        chunkRepository.save(Chunk(user, sha256(content), content))

        val found = chunkRepository.findByUserIdAndContentHashIn(user.id!!, listOf(sha256(content))).single()

        assertArrayEquals(content, found.content)
        assertArrayEquals(sha256(content), found.contentHash)
    }

    @Test
    fun sameChunkCanRepeatInOneVersion() {
        val user = userService.create("tester", "test-${UUID.randomUUID()}@example.com", "password123")
        val document = documentService.create(user.id!!, "문서")
        val version = documentService.saveVersion(document.id!!, null, UUID.randomUUID().toString(), user.id!!)
        val a = chunkRepository.save(Chunk(user, sha256("a".toByteArray()), "a".toByteArray()))
        val b = chunkRepository.save(Chunk(user, sha256("b".toByteArray()), "b".toByteArray()))

        listOf(a, b, a).forEachIndexed { pos, chunk ->
            versionChunkRepository.save(VersionChunk(VersionChunkId(version.id!!, pos), chunk))
        }

        val contents = versionChunkRepository.findContentsInOrder(version.id!!)
        assertEquals("aba", contents.joinToString("") { String(it) })
    }
}
