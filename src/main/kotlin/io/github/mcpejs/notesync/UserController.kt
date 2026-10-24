package io.github.mcpejs.notesync

import org.springframework.web.bind.annotation.*

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateUserRequest(
    @NotBlank(message = "이름은 비어 있을 수 없습니다")
    @Size(max = 20, message = "이름은 20자 이하여야 합니다")
    val name: String,

    @NotBlank(message = "이메일은 비어 있을 수 없습니다")
    @Size(max = 100, message = "이메일은 100자 이하여야 합니다")
    @Email
    val email: String,
)

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping
    fun create(
        @Valid
        @RequestBody request: CreateUserRequest): User =
        userService.create(request.name, request.email)

    @GetMapping
    fun list(): List<User> = userService.findAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): User = userService.findById(id)
}