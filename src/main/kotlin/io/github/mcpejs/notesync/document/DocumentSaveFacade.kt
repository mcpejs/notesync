package io.github.mcpejs.notesync.document

import org.springframework.stereotype.Service

@Service
class DocumentSaveFacade(private val documentService: DocumentService) {

    fun save(documentId: Long, baseVersionId: Long?, requestId: String, requesterId: Long, content: ByteArray): DocumentVersion {
        val chunks = prepareChunks(content)
        if(chunks.size > MAX_CHUNKS_PER_VERSION) throw TooManyChunksException(chunks.size)
        try {
            return documentService.saveVersion(documentId, baseVersionId, requestId, requesterId, chunks)
        } catch (e: VersionConflictException) {
            throw documentService.createConflictCopy(documentId, requestId, requesterId, chunks)
        }
    }
}