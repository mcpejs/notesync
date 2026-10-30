package io.github.mcpejs.notesync.auth

import io.github.mcpejs.notesync.user.UserResponse
import io.github.mcpejs.notesync.user.UserService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class SignupRequest(
    @NotBlank(message = "이름은 비어 있을 수 없습니다")
    @Size(max = 20, message = "이름은 20자 이하여야 합니다")
    val name: String,

    @NotBlank(message = "이메일은 비어 있을 수 없습니다")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다")
    @Email
    val email: String,

    @NotBlank(message = "비밀번호는 비어 있을 수 없습니다")
    @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다")
    val password: String,
)

data class LoginRequest(
    @NotBlank(message = "이메일은 비어 있을 수 없습니다")
    val email: String,

    @NotBlank(message = "비밀번호는 비어 있을 수 없습니다")
    val password: String,
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
)

@RestController
@RequestMapping("/auth")
class AuthController(
    private val userService: UserService,
    private val jwtProvider: JwtProvider,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): UserResponse =
        UserResponse.from(userService.create(request.name, request.email, request.password))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): LoginResponse {
        val user = userService.login(request.email, request.password)
        return LoginResponse(
            accessToken = jwtProvider.createToken(user.id!!),
            expiresIn = jwtProvider.expirationSeconds,
        )
    }
}
