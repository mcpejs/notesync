package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.auth.JwtProvider
import io.github.mcpejs.notesync.user.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// 사용자 행 잠금은 그 사용자의 저장만 줄 세운다. 다른 사용자의 저장은 기다리지 않는다
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserLockIsolationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtProvider: JwtProvider
    @Autowired lateinit var documentService: DocumentService
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var transactionManager: PlatformTransactionManager

    private fun newUserId() = userService.create("tester", "test-${UUID.randomUUID()}@example.com", "password123").id!!

    private fun saveStatus(documentId: Long, userId: Long): Int = mockMvc.post("/documents/$documentId/versions") {
        header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
        header("Idempotency-Key", UUID.randomUUID().toString())
        contentType = MediaType.APPLICATION_JSON
        content = """{"baseVersionId": null, "content": "같은 내용"}"""
    }.andReturn().response.status

    @Test
    fun otherUsersSaveIsNotBlockedByUserRowLock() {
        val userA = newUserId()
        val userB = newUserId()
        val docA = documentService.create(userA, "A의 문서").id!!
        val docB = documentService.create(userB, "B의 문서").id!!

        val pool = Executors.newFixedThreadPool(3)
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)

        // 저장 경로와 같은 메서드로 A의 행을 잠그고, 커밋하지 않은 채 쥐고 있는다
        val holder = pool.submit {
            TransactionTemplate(transactionManager).executeWithoutResult {
                userService.findByIdForUpdate(userA)
                locked.countDown()
                release.await()
            }
        }
        try {
            assertEquals(true, locked.await(5, TimeUnit.SECONDS))

            val startB = System.nanoTime()
            val saveB: Future<Int> = pool.submit<Int> { saveStatus(docB, userB) }
            val statusB = try {
                saveB.get(5, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                fail("B의 저장이 A의 잠금 때문에 5초 넘게 기다렸습니다")
            }
            assertEquals(201, statusB)
            println("B 저장: ${(System.nanoTime() - startB) / 1_000_000}ms")

            // 대조군: 같은 상태에서 A의 저장은 잠금을 기다린다
            val startA = System.nanoTime()
            val saveA: Future<Int> = pool.submit<Int> { saveStatus(docA, userA) }
            assertThrows<TimeoutException> { saveA.get(3, TimeUnit.SECONDS) }

            release.countDown()
            assertEquals(201, saveA.get(10, TimeUnit.SECONDS))
            println("A 저장: ${(System.nanoTime() - startA) / 1_000_000}ms (잠금을 푼 뒤 끝남)")
            holder.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            pool.shutdown()
            pool.awaitTermination(15, TimeUnit.SECONDS)
        }
    }
}
