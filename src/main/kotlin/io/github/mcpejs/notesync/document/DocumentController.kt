package io.github.mcpejs.notesync.document

import org.springframework.web.bind.annotation.*

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import java.util.UUID

data class CreateDocumentRequest(
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    val title:String? = null,
)


data class DocumentResponse(
    val id: Long,
    val title: String,
) {
    companion object {
        fun from(doc: Document) = DocumentResponse(doc.id!!, doc.title)
    }
}

data class SaveVersionRequest(
    val baseVersionId: Long? = null,
)

data class VersionResponse(
    val id: Long,
    val documentId: Long,
    val num: Int,
) {
    companion object {
        fun from(version: DocumentVersion) = VersionResponse(version.id!!, version.document.id!!, version.num)
    }
}

@RestController
@RequestMapping("/documents")
class DocumentController(
    private val documentService: DocumentService,
    private val documentSaveFacade: DocumentSaveFacade,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid
        @RequestBody request: CreateDocumentRequest,
        @RequestAttribute userId: Long
    ): DocumentResponse= DocumentResponse.from(documentService.create(userId, request.title))


    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @RequestAttribute userId: Long,
    ): DocumentResponse = DocumentResponse.from(documentService.findById(id, userId))

    @GetMapping
    fun list(
        @RequestAttribute userId: Long,
    ): List<DocumentResponse> = documentService.findByUserId(userId).map { DocumentResponse.from(it) }

    // 키를 UUID로 받아서 형식이 틀리면 400, CHAR(36)을 넘는 값은 DB까지 가지 않는다
    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    fun saveVersion(
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") requestId: UUID,
        @RequestBody request: SaveVersionRequest,
        @RequestAttribute userId: Long,
    ): VersionResponse = VersionResponse.from(
        documentSaveFacade.save(id, request.baseVersionId, requestId.toString(), userId)
    )
}