package io.github.mcpejs.notesync.user

import org.springframework.web.bind.annotation.*

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

    @GetMapping("/me")
    fun me(@RequestAttribute userId: Long): UserResponse = UserResponse.from(userService.findById(userId))
}
