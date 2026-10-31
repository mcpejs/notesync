package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.auth.JwtProvider
import io.github.mcpejs.notesync.user.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentVersionTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtProvider: JwtProvider
    @Autowired lateinit var documentService: DocumentService
    @Autowired lateinit var documentSaveFacade: DocumentSaveFacade
    @Autowired lateinit var userService: UserService

    private fun newUserId(): Long {
        val email = "test-${UUID.randomUUID()}@example.com"
        return userService.create("tester", email, "password123").id!!
    }

    private fun newKey() = UUID.randomUUID().toString()

    @Test
    fun savesGetIncreasingNumAndMoveCurrent() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")

        val v1 = documentService.saveVersion(doc.id!!, null, newKey(), userId)
        val v2 = documentService.saveVersion(doc.id!!, v1.id, newKey(), userId)

        assertEquals(1, v1.num)
        assertEquals(2, v2.num)
        assertEquals(v2.id, documentService.findById(doc.id!!, userId).currentVersionId)
    }

    @Test
    fun retryWithSameKeyReturnsSameVersion() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val key = newKey()

        val first = documentService.saveVersion(doc.id!!, null, key, userId)
        val retry = documentService.saveVersion(doc.id!!, null, key, userId)

        assertEquals(first.id, retry.id)
        assertEquals(first.id, documentService.findById(doc.id!!, userId).currentVersionId)
    }

    @Test
    fun otherUserCannotReuseKey() {
        val ownerId = newUserId()
        val otherId = newUserId()
        val doc = documentService.create(ownerId, "문서")
        val key = newKey()
        documentService.saveVersion(doc.id!!, null, key, ownerId)

        assertThrows<DocumentNotFoundException> {
            documentService.saveVersion(doc.id!!, null, key, otherId)
        }
    }

    @Test
    fun otherUserCannotReuseKeyOnOwnDocument() {
        val ownerId = newUserId()
        val otherId = newUserId()
        val key = newKey()
        documentService.saveVersion(documentService.create(ownerId, "문서").id!!, null, key, ownerId)
        val otherDoc = documentService.create(otherId, "문서")

        assertThrows<DocumentNotFoundException> {
            documentService.saveVersion(otherDoc.id!!, null, key, otherId)
        }
    }

    private fun saveAtOnce(documentId: Long, baseVersionId: Long?, keys: List<String>, userId: Long): List<Result<DocumentVersion>> {
        val pool = Executors.newFixedThreadPool(keys.size)
        val start = CountDownLatch(1)
        val saves = keys.map { key ->
            pool.submit<Result<DocumentVersion>> {
                start.await()
                runCatching { documentSaveFacade.save(documentId, baseVersionId, key, userId) }
            }
        }
        start.countDown()
        val results = saves.map { it.get() }
        pool.shutdown()
        return results
    }

    // 잠금 전에는 40번 중 39번 교착으로 500이 났다
    @Test
    fun concurrentSavesLeaveOneWinnerAndOneCopy() {
        repeat(40) {
            val userId = newUserId()
            val doc = documentService.create(userId, "회의록")
            val v1 = documentService.saveVersion(doc.id!!, null, newKey(), userId)

            val results = saveAtOnce(doc.id!!, v1.id, listOf(newKey(), newKey()), userId)

            val winner = results.single { it.isSuccess }.getOrThrow()
            val loser = results.single { it.isFailure }.exceptionOrNull()
            assertEquals(2, winner.num)
            assertEquals(winner.id, (loser as ConflictCopyCreatedException).currentVersionId)
            assertEquals(2, documentService.findByUserId(userId).size)
        }
    }

    @Test
    fun concurrentRetriesOfConflictCreateOneCopy() {
        repeat(40) {
            val userId = newUserId()
            val doc = documentService.create(userId, "회의록")
            val v1 = documentService.saveVersion(doc.id!!, null, newKey(), userId)
            documentService.saveVersion(doc.id!!, v1.id, newKey(), userId)
            val key = newKey()

            val results = saveAtOnce(doc.id!!, v1.id, listOf(key, key), userId)

            val copyIds = results.map { (it.exceptionOrNull() as ConflictCopyCreatedException).copyId }
            assertEquals(1, copyIds.toSet().size)
            assertEquals(2, documentService.findByUserId(userId).size)
        }
    }

    @Test
    fun staleBaseCreatesCopyAndKeepsOriginal() {
        val userId = newUserId()
        val doc = documentService.create(userId, "회의록")
        val v1 = documentService.saveVersion(doc.id!!, null, newKey(), userId)
        val v2 = documentService.saveVersion(doc.id!!, v1.id, newKey(), userId)

        val conflict = assertThrows<ConflictCopyCreatedException> {
            documentSaveFacade.save(doc.id!!, v1.id, newKey(), userId)
        }

        // 사본이 커밋되어 다시 읽히고, 원본의 current는 그대로
        val copy = documentService.findById(conflict.copyId, userId)
        assertEquals(conflict.copyTitle, copy.title)
        assertNotNull(copy.currentVersionId)
        assertEquals(v2.id, conflict.currentVersionId)
        assertEquals(v2.id, documentService.findById(doc.id!!, userId).currentVersionId)
    }

    @Test
    fun conflictRetryReturnsSameCopy() {
        val userId = newUserId()
        val doc = documentService.create(userId, "회의록")
        val v1 = documentService.saveVersion(doc.id!!, null, newKey(), userId)
        documentService.saveVersion(doc.id!!, v1.id, newKey(), userId)
        val key = newKey()

        val first = assertThrows<ConflictCopyCreatedException> {
            documentSaveFacade.save(doc.id!!, v1.id, key, userId)
        }
        val retry = assertThrows<ConflictCopyCreatedException> {
            documentSaveFacade.save(doc.id!!, v1.id, key, userId)
        }

        assertEquals(first.copyId, retry.copyId)
        assertEquals(first.currentVersionId, retry.currentVersionId)
        assertEquals(2, documentService.findByUserId(userId).size)
    }

    @Test
    fun longTitleCopyFitsColumn() {
        val userId = newUserId()
        val doc = documentService.create(userId, "가".repeat(100))
        documentService.saveVersion(doc.id!!, null, newKey(), userId)

        val conflict = assertThrows<ConflictCopyCreatedException> {
            documentSaveFacade.save(doc.id!!, null, newKey(), userId)
        }

        assertEquals(100, conflict.copyTitle.length)
    }

    @Test
    fun conflictApiReturns409WithCopy() {
        val userId = newUserId()
        val doc = documentService.create(userId, "회의록")
        val v1 = documentService.saveVersion(doc.id!!, null, newKey(), userId)

        mockMvc.post("/documents/${doc.id}/versions") {
            header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
            header("Idempotency-Key", newKey())
            contentType = MediaType.APPLICATION_JSON
            content = """{"baseVersionId": null}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
            jsonPath("$.currentVersionId") { value(v1.id!!) }
            jsonPath("$.conflictCopyId") { exists() }
        }
    }

    @Test
    fun saveVersionApi() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")

        mockMvc.post("/documents/${doc.id}/versions") {
            header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
            header("Idempotency-Key", newKey())
            contentType = MediaType.APPLICATION_JSON
            content = """{"baseVersionId": null}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.num") { value(1) }
        }
    }

    @Test
    fun malformedKeyIsRejected() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")

        mockMvc.post("/documents/${doc.id}/versions") {
            header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
            header("Idempotency-Key", "not-a-uuid")
            contentType = MediaType.APPLICATION_JSON
            content = """{"baseVersionId": null}"""
        }.andExpect { status { isBadRequest() } }
    }
}
