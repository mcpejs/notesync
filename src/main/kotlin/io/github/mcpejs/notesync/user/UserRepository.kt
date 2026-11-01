package io.github.mcpejs.notesync.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    fun findByIdForUpdate(id: Long): User?
}