package io.github.mcpejs.notesync.user

import io.github.mcpejs.notesync.auth.InvalidCredentialsException

import org.springframework.stereotype.Service
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    // 없는 이메일도 matches를 한 번 돌려서 응답 시간으로 계정 존재가 드러나지 않게 한다
    private val dummyHash = requireNotNull(passwordEncoder.encode("dummy-password"))

    fun login(email: String, password: String): User {
        val user = userRepository.findByEmail(email)
        val matched = passwordEncoder.matches(password, user?.passwordHash ?: dummyHash)
        if (user == null || !matched) throw InvalidCredentialsException()
        return user
    }

    fun create(name: String, email: String, password: String): User {
        val user = User(name, email, requireNotNull(passwordEncoder.encode(password)))
        return userRepository.save(user)
    }

    fun findAll(): List<User> = userRepository.findAll()

    fun findById(id: Long): User = userRepository.findByIdOrNull(id) ?: throw UserNotFoundException(id)

    fun findByIdForUpdate(id: Long): User=userRepository.findByIdForUpdate(id) ?: throw UserNotFoundException(id)
}
