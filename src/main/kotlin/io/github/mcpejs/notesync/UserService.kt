package io.github.mcpejs.notesync

import org.springframework.stereotype.Service

@Service
class UserService {
    private val users = mutableListOf<User>()
    private var nextId = 1L

    fun create(name: String): User {
        val user = User(nextId++, name)
        users.add(user)
        return user
    }

    fun findAll(): List<User> = users
}