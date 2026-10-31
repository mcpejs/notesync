package io.github.mcpejs.notesync.document

import org.springframework.data.jpa.repository.JpaRepository

interface DocumentVersionRepository : JpaRepository<DocumentVersion, Long> {
    fun findByClientRequestId(clientRequestId: String): DocumentVersion?
    fun findTopByDocumentIdOrderByNumDesc(documentId: Long): DocumentVersion?
}
