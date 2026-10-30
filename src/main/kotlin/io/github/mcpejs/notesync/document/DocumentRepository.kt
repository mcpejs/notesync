package io.github.mcpejs.notesync.document

import org.springframework.data.jpa.repository.JpaRepository

interface DocumentRepository : JpaRepository<Document, Long> {
    fun findByUserId(userId: Long): List<Document>
}