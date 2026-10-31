package io.github.mcpejs.notesync.document

import org.springframework.stereotype.Service

@Service
class DocumentSaveFacade(private val documentService: DocumentService) {

    fun save(documentId: Long, baseVersionId: Long?, requestId: String, requesterId: Long): DocumentVersion {
        try {
            return documentService.saveVersion(documentId, baseVersionId, requestId, requesterId)
        } catch (e: VersionConflictException) {
            throw documentService.createConflictCopy(documentId, requestId, requesterId)
        }
    }
}