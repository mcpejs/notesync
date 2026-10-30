package io.github.mcpejs.notesync.user

import org.springframework.web.bind.annotation.*

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus

data class CreateUserRequest(
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

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
) {
    companion object {
        fun from(user: User) = UserResponse(user.id!!, user.name, user.email)
    }
}

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid
        @RequestBody request: CreateUserRequest): UserResponse =
        UserResponse.from(userService.create(request.name, request.email, request.password))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): UserResponse =
        UserResponse.from(userService.login(request.email, request.password))

    @GetMapping
    fun list(): List<UserResponse> = userService.findAll().map { UserResponse.from(it) }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): UserResponse = UserResponse.from(userService.findById(id))
}