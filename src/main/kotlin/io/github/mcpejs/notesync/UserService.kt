package io.github.mcpejs.notesync

import org.springframework.stereotype.Service
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun create(name: String, email: String, password: String): User {
        val user = User(name, email, requireNotNull(passwordEncoder.encode(password)))
        return userRepository.save(user)
    }

    fun findAll(): List<User> = userRepository.findAll()

    fun findById(id: Long): User = userRepository.findByIdOrNull(id) ?: throw UserNotFoundException(id)
}
