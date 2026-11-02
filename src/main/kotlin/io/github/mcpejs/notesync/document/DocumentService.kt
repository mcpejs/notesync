package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.user.User
import io.github.mcpejs.notesync.user.UserService
import org.springframework.data.repository.findByIdOrNull

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.HexFormat

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val userService: UserService,
    private val documentVersionRepository: DocumentVersionRepository,
    private val chunkRepository: ChunkRepository,
    private val versionChunkRepository: VersionChunkRepository
) {
    companion object{
        const val DEFAULT_TITLE = "제목없음"
        const val DEFAULT_COPY_TITLE_SUFFIX = "(충돌 사본)"
    }

    // 트랜잭션의 첫 쿼리로 불러야 한다. 먼저 읽은 문서나 스냅샷이 있으면 기다린 뒤에도 낡은 값을 본다
    private fun findOwnedForUpdate(documentId: Long, requesterId: Long): Document {
        userService.findByIdForUpdate(requesterId)
        val document=documentRepository.findByIdForUpdate(documentId) ?: throw DocumentNotFoundException(documentId)
        if(document.user.id != requesterId){
            throw DocumentNotFoundException(documentId)
        }
        return document
    }

    private fun conflictCopyOf(copyVersion: DocumentVersion, originalDocument: Document) = ConflictCopyCreatedException(
        copyVersion.document.id!!,
        copyVersion.document.title,
        originalDocument.currentVersionId,
    )

    fun create(userId: Long, title: String?): Document {
        val user = userService.findById(userId)
        val document = Document(if(title.isNullOrBlank()) DEFAULT_TITLE else title, user)
        return documentRepository.save(document)
    }

    // 주인이 아니면 없는 문서와 똑같이 404 (존재 여부를 숨긴다)
    fun findById(documentId: Long, requesterId: Long): Document {
        val document = documentRepository.findByIdOrNull(documentId) ?: throw DocumentNotFoundException(documentId)
        val documentOwnerId = document.user.id
        if(documentOwnerId != requesterId) throw DocumentNotFoundException(documentId)
        return document
    }

    fun findByUserId(userId: Long): List<Document> {
        userService.findById(userId)
        val documents = documentRepository.findByUserId(userId)
        return documents
    }

    @Transactional
    fun saveVersion(documentId: Long, baseVersionId: Long?, requestId: String, requesterId: Long, chunks: List<PreparedChunk>): DocumentVersion {
        val document = findOwnedForUpdate(documentId, requesterId)

        val existDocumentVersion = documentVersionRepository.findByClientRequestId(requestId)
        if(existDocumentVersion != null) {
//            권한 없음
            if(existDocumentVersion.document.user.id != requesterId)
                throw DocumentNotFoundException(documentId)
//            문서는 있지만 사본임
            if(existDocumentVersion.document.id != documentId)
                throw conflictCopyOf(existDocumentVersion, document)

            return existDocumentVersion
        }

        if(document.currentVersionId != baseVersionId){
            throw VersionConflictException()
        }

        val latestVersionNum = documentVersionRepository.findTopByDocumentIdOrderByNumDesc(documentId)?.num ?: 0
        val nextVersionNum = latestVersionNum + 1

        val documentVersion = documentVersionRepository.save(DocumentVersion(document, nextVersionNum, requestId))
        document.currentVersionId = documentVersion.id
        saveVersionChunks(documentVersion, document.user, chunks)
        return documentVersion
    }

    @Transactional
    fun createConflictCopy(documentId: Long, requestId: String, requesterId: Long, chunks: List<PreparedChunk>): ConflictCopyCreatedException {
        val originalDocument = findOwnedForUpdate(documentId, requesterId)

//        같은 키가 동시에 와서 잠금을 기다리는 사이 사본이 이미 만들어졌을 수 있다
        val existCopyVersion = documentVersionRepository.findByClientRequestId(requestId)
        if(existCopyVersion != null) {
            if(existCopyVersion.document.user.id != requesterId)
                throw DocumentNotFoundException(documentId)
            return conflictCopyOf(existCopyVersion, originalDocument)
        }

        val copyTitle= originalDocument.title.take(100-DEFAULT_COPY_TITLE_SUFFIX.length) + DEFAULT_COPY_TITLE_SUFFIX
        val copyDocument=create(requesterId, copyTitle)

        val copyDocumentVersion = documentVersionRepository.save(DocumentVersion(copyDocument, 1, requestId))
        copyDocument.currentVersionId = copyDocumentVersion.id
        saveVersionChunks(copyDocumentVersion, copyDocument.user, chunks)
        return ConflictCopyCreatedException(copyDocument.id!!, copyTitle, originalDocument.currentVersionId)
    }

    private fun saveVersionChunks(version: DocumentVersion, user: User, chunks: List<PreparedChunk>) {
        if(chunks.isEmpty()) return

        val byKey = mutableMapOf<String, Chunk>()
        val hashes=chunks.distinctBy { it.hashKey }.map { it.hash }
        for(chunk in chunkRepository.findByUserIdAndContentHashIn(user.id!!, hashes)) {
            byKey[HexFormat.of().formatHex(chunk.contentHash)] = chunk
        }
        chunks.forEachIndexed { pos, chunk ->
            val saved=byKey[chunk.hashKey]
                ?: chunkRepository.save(Chunk(user, chunk.hash, chunk.content))
                    .also { byKey[chunk.hashKey] = it }
            versionChunkRepository.save(VersionChunk(VersionChunkId(version.id!!, pos),saved))
        }
    }
}
