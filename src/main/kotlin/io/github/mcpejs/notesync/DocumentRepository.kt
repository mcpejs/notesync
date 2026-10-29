package io.github.mcpejs.notesync

import org.springframework.data.jpa.repository.JpaRepository

interface DocumentRepository : JpaRepository<Document, Long> {
    fun findByUserId(userId: Long): List<Document>
}