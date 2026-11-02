package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.auth.JwtProvider
import io.github.mcpejs.notesync.user.UserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VersionContentApiTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtProvider: JwtProvider
    @Autowired lateinit var documentService: DocumentService
    @Autowired lateinit var documentSaveFacade: DocumentSaveFacade
    @Autowired lateinit var userService: UserService

    private fun newUserId() = userService.create("tester", "test-${UUID.randomUUID()}@example.com", "password123").id!!

    private fun save(documentId: Long, baseVersionId: Long?, userId: Long, content: String) =
        documentSaveFacade.save(documentId, baseVersionId, UUID.randomUUID().toString(), userId, content.toByteArray())

    private fun getContent(documentId: Long, versionId: Long, userId: Long) =
        mockMvc.get("/documents/$documentId/versions/$versionId") {
            header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
        }

    @Test
    fun ownerReadsContent() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val content = "첫 문단\r\n\r\n둘째 문단"
        val version = save(doc.id!!, null, userId, content)

        getContent(doc.id!!, version.id!!, userId).andExpect {
            status { isOk() }
            jsonPath("$.documentId") { value(doc.id!!) }
            jsonPath("$.versionId") { value(version.id!!) }
            jsonPath("$.content") { value(content) }
        }
    }

    // 청크 경계가 글자 중간에 걸려도 합친 뒤 변환하면 깨지지 않는다
    @Test
    fun characterCutByChunkLimitReadsBack() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val content = "가".repeat(30_000)
        val version = save(doc.id!!, null, userId, content)

        getContent(doc.id!!, version.id!!, userId).andExpect {
            status { isOk() }
            jsonPath("$.content") { value(content) }
        }
    }

    @Test
    fun oldVersionStaysReadable() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val v1 = save(doc.id!!, null, userId, "처음")
        save(doc.id!!, v1.id, userId, "다음")

        getContent(doc.id!!, v1.id!!, userId).andExpect {
            status { isOk() }
            jsonPath("$.content") { value("처음") }
        }
    }

    @Test
    fun otherUsersDocumentIs404() {
        val ownerId = newUserId()
        val doc = documentService.create(ownerId, "문서")
        val version = save(doc.id!!, null, ownerId, "비밀")

        getContent(doc.id!!, version.id!!, newUserId()).andExpect { status { isNotFound() } }
    }

    // 자기 문서 id에 남의 버전 id를 붙여도 읽을 수 없다
    @Test
    fun otherUsersVersionUnderOwnDocumentIs404() {
        val ownerId = newUserId()
        val otherId = newUserId()
        val secret = save(documentService.create(ownerId, "문서").id!!, null, ownerId, "비밀")
        val ownDoc = documentService.create(otherId, "문서")

        getContent(ownDoc.id!!, secret.id!!, otherId).andExpect { status { isNotFound() } }
    }

    @Test
    fun versionOfAnotherOwnDocumentIs404() {
        val userId = newUserId()
        val version = save(documentService.create(userId, "문서").id!!, null, userId, "내용")
        val anotherDoc = documentService.create(userId, "문서")

        getContent(anotherDoc.id!!, version.id!!, userId).andExpect { status { isNotFound() } }
    }

    @Test
    fun newDocumentHasNullCurrentVersion() {
        val userId = newUserId()

        mockMvc.post("/documents") {
            header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
            contentType = MediaType.APPLICATION_JSON
            content = """{"title": "새 문서"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.currentVersionId") { value(null as Any?) }
        }
    }

    @Test
    fun documentShowsCurrentVersion() {
        val userId = newUserId()
        val doc = documentService.create(userId, "문서")
        val version = save(doc.id!!, null, userId, "내용")

        mockMvc.get("/documents/${doc.id}") {
            header("Authorization", "Bearer ${jwtProvider.createToken(userId)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.currentVersionId") { value(version.id!!) }
        }
    }
}
