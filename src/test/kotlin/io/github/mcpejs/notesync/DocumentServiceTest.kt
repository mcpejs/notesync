package io.github.mcpejs.notesync

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
}