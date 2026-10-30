package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.user.UserNotFoundException
import io.github.mcpejs.notesync.user.UserService

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID


@SpringBootTest
@ActiveProfiles("test")
class DocumentServiceTest {
    @Autowired lateinit var documentService: DocumentService
    @Autowired lateinit var userService: UserService

    private fun newUserId(): Long {
        val email = "test-${UUID.randomUUID()}@example.com"
        return userService.create("tester", email, "password123").id!!
    }

    @Test
    fun nullBlankTitleGetsDefault() {
        val userId = newUserId()
        val doc=documentService.create(userId,null)
        assertEquals(userId,doc.user.id)
        assertEquals(DocumentService.DEFAULT_TITLE, doc.title)

//        Blank는 짧게
        assertEquals(DocumentService.DEFAULT_TITLE, documentService.create(userId,"     ").title)
    }

    @Test
    fun documentList() {
        val userId = newUserId()

        assertTrue(documentService.findByUserId(userId).isEmpty())
        assertThrows<UserNotFoundException>{
            documentService.findByUserId(9999999999L)
        }
    }

    @Test
    fun ownerGetsRequestedDocument() {
        val ownerId = newUserId()
        val doc = documentService.create(ownerId, "내 문서")

        val found = documentService.findById(doc.id!!, ownerId)

        // 성공 여부가 아니라 "요청한 그 문서"가 왔는지 본다
        assertEquals(doc.id, found.id)
    }

    @Test
    fun otherUserCannotSeeDocument() {
        val ownerId = newUserId()
        val otherId = newUserId()
        val doc = documentService.create(ownerId, "내 문서")

        assertThrows<DocumentNotFoundException> {
            documentService.findById(doc.id!!, otherId)
        }
        assertThrows<DocumentNotFoundException> {
            documentService.findById(9999999999L, ownerId)
        }
    }
}