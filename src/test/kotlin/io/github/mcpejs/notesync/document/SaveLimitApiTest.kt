package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.auth.JwtProvider
import io.github.mcpejs.notesync.common.MAX_BODY_BYTES
import io.github.mcpejs.notesync.user.UserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaveLimitApiTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtProvider: JwtProvider
    @Autowired lateinit var documentService: DocumentService
    @Autowired lateinit var userService: UserService

    private fun newUserId() = userService.create("tester", "test-${UUID.randomUUID()}@example.com", "password123").id!!

    private fun saveBody(documentId: Long, userId: Long?, body: String?) = mockMvc.post("/documents/$documentId/versions") {
        if (userId != null) header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
        header("Idempotency-Key", UUID.randomUUID().toString())
        contentType = MediaType.APPLICATION_JSON
        if (body != null) content = body
    }

    private fun jsonOfSize(bytes: Long): String {
        val wrapper = """{"baseVersionId": null, "content": ""}"""
        return wrapper.replace("\"content\": \"\"", "\"content\": \"${"a".repeat(bytes.toInt() - wrapper.length)}\"")
    }

    @Test
    fun bodyAtLimitIsSaved() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")

        saveBody(doc.id!!, userId, jsonOfSize(MAX_BODY_BYTES)).andExpect { status { isCreated() } }
    }

    @Test
    fun bodyOverLimitIs413() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")

        saveBody(doc.id!!, userId, jsonOfSize(MAX_BODY_BYTES + 1)).andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value("BODY_TOO_LARGE") }
        }
    }

    @Test
    fun missingLengthIs411() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")

        saveBody(doc.id!!, userId, null).andExpect { status { isEqualTo(411) } }
    }

    @Test
    fun largeBodyWithoutTokenIs401() {
        val ownerId = newUserId()
        val doc = documentService.create(ownerId, "문서")

        saveBody(doc.id!!, null, jsonOfSize(MAX_BODY_BYTES + 1)).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun chunksAtLimitAreSaved() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val content = "a\\n\\n".repeat(MAX_CHUNKS_PER_VERSION)

        saveBody(doc.id!!, userId, """{"content": "$content"}""").andExpect { status { isCreated() } }
    }

    @Test
    fun chunksOverLimitAre413() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val content = "a\\n\\n".repeat(MAX_CHUNKS_PER_VERSION + 1)

        saveBody(doc.id!!, userId, """{"content": "$content"}""").andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value("TOO_MANY_CHUNKS") }
        }
    }
}
