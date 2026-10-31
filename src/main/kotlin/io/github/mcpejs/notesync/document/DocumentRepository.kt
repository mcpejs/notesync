package io.github.mcpejs.notesync.document

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface DocumentRepository : JpaRepository<Document, Long> {
    fun findByUserId(userId: Long): List<Document>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    fun findByIdForUpdate(id: Long): Document?
}