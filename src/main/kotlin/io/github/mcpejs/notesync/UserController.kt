package io.github.mcpejs.notesync

import org.springframework.web.bind.annotation.*

data class CreateUserRequest(val name: String)

@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping
    fun create(@RequestBody request: CreateUserRequest): User =
        userService.create(request.name)

    @GetMapping
    fun list(): List<User> = userService.findAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): User = userService.findById(id)
}