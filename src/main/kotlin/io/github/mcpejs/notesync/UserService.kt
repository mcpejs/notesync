package io.github.mcpejs.notesync

import org.springframework.stereotype.Service
import org.springframework.data.repository.findByIdOrNull

@Service
class UserService(private val userRepository: UserRepository) {
    fun create(name: String, email: String): User {
        val user = User(name, email)
        return userRepository.save(user)

    }

    fun findAll(): List<User> = userRepository.findAll()

    fun findById(id: Long): User = userRepository.findByIdOrNull(id) ?: throw UserNotFoundException(id)
}