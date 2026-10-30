package io.github.mcpejs.notesync.auth

import io.github.mcpejs.notesync.document.DocumentService
import io.github.mcpejs.notesync.user.UserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc       // MockMvc 객체를 만들어 빈으로 등록
@ActiveProfiles("test")
class AuthApiTest {
    private fun newUserToken(userId: Long): String {
        val token=jwtProvider.createToken(userId)
        return token
    }

    @Autowired
    lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtProvider: JwtProvider
    @Autowired lateinit var userService: UserService
    @Autowired lateinit var documentService: DocumentService

    @Test
    fun signupWithoutToken(){
        val email = "test-${UUID.randomUUID()}@example.com"
        mockMvc.post("/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "t", "email": "$email", "password": "password123"}"""
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun noTokenIsRejected(){
        mockMvc.get("/documents"){

        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun tamperedTokenIsRejected(){
        val token=newUserToken(1)
        mockMvc.get("/documents"){
            header("Authorization", "Bearer {$token}modified")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun validTokenIsAccepted(){
        val email = "test-${UUID.randomUUID()}@example.com"
        val user = userService.create("validTokenIsAccepted",email,"password123")

        val token=newUserToken(user.id!!)

        mockMvc.get("/documents"){
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isEmpty() }
        }

    }
}