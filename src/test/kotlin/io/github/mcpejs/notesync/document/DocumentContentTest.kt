package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.user.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
@ActiveProfiles("test")
class DocumentContentTest {
    @Autowired lateinit var documentService: DocumentService
    @Autowired lateinit var documentSaveFacade: DocumentSaveFacade
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var chunkRepository: ChunkRepository
    @Autowired lateinit var versionChunkRepository: VersionChunkRepository

    private fun newUserId() = userService.create("tester", "test-${UUID.randomUUID()}@example.com", "password123").id!!

    private fun newKey() = UUID.randomUUID().toString()

    private fun save(documentId: Long, baseVersionId: Long?, userId: Long, content: String, key: String = newKey()) =
        documentSaveFacade.save(documentId, baseVersionId, key, userId, content.toByteArray())

    private fun contentOf(versionId: Long) =
        versionChunkRepository.findContentsInOrder(versionId).joinToString("") { String(it) }

    private fun storedChunks(userId: Long, content: String) =
        chunkRepository.findByUserIdAndContentHashIn(userId, prepareChunks(content.toByteArray()).map { it.hash }).size

    @Test
    fun savedContentRestoresWithRepeatedParagraph() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val content = "a\n\nb\n\na\n\n"

        val version = save(doc.id!!, null, userId, content)

        assertEquals(content, contentOf(version.id!!))
        assertEquals(2, storedChunks(userId, content))
    }

    @Test
    fun emptyContentRestoresEmpty() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")

        val version = save(doc.id!!, null, userId, "")

        assertEquals("", contentOf(version.id!!))
        assertEquals(version.id, documentService.findById(doc.id!!, userId).currentVersionId)
    }

    @Test
    fun nextVersionReusesUnchangedParagraph() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val v1 = save(doc.id!!, null, userId, "a\n\nb")

        val v2 = save(doc.id!!, v1.id, userId, "a\n\nc")

        assertEquals("a\n\nb", contentOf(v1.id!!))
        assertEquals("a\n\nc", contentOf(v2.id!!))
        assertEquals(3, storedChunks(userId, "a\n\nb") + storedChunks(userId, "c"))
    }

    @Test
    fun retryDoesNotStoreTwice() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val key = newKey()

        val first = save(doc.id!!, null, userId, "a\n\nb", key)
        val retry = save(doc.id!!, null, userId, "a\n\nb", key)

        assertEquals(first.id, retry.id)
        assertEquals("a\n\nb", contentOf(first.id!!))
        assertEquals(2, storedChunks(userId, "a\n\nb"))
    }

    @Test
    fun conflictCopyKeepsLosersContent() {
        val userId = newUserId()
        val doc = documentService.create(userId, "회의록")
        val v1 = save(doc.id!!, null, userId, "처음")
        val v2 = save(doc.id!!, v1.id, userId, "기기 A")

        val conflict = assertThrows<ConflictCopyCreatedException> {
            save(doc.id!!, v1.id, userId, "기기 B")
        }

        val copy = documentService.findById(conflict.copyId, userId)
        assertEquals("기기 B", contentOf(copy.currentVersionId!!))
        assertEquals("기기 A", contentOf(v2.id!!))
    }

    @Test
    fun usersDoNotShareChunks() {
        val ownerId = newUserId()
        val otherId = newUserId()

        save(documentService.create(ownerId, "문서").id!!, null, ownerId, "같은 문단")
        val other = save(documentService.create(otherId, "문서").id!!, null, otherId, "같은 문단")

        assertEquals("같은 문단", contentOf(other.id!!))
        assertEquals(1, storedChunks(ownerId, "같은 문단"))
        assertEquals(1, storedChunks(otherId, "같은 문단"))
    }

    // 문서가 달라 문서 잠금으로는 줄이 서지 않는다. 사용자 행 잠금이 지킨다
    @Test
    fun twoDocumentsSaveSameNewChunkAtOnce() {
        repeat(40) {
            val userId = newUserId()
            val docs = List(2) { documentService.create(userId, "문서").id!! }
            val content = "같은 문단\n\n${UUID.randomUUID()}"
            val pool = Executors.newFixedThreadPool(2)
            val start = CountDownLatch(1)

            val saves = docs.map { docId ->
                pool.submit<DocumentVersion> {
                    start.await()
                    save(docId, null, userId, content)
                }
            }
            start.countDown()
            val versions = saves.map { it.get() }
            pool.shutdown()

            versions.forEach { assertEquals(content, contentOf(it.id!!)) }
            assertEquals(2, storedChunks(userId, content))
        }
    }
}
