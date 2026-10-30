package io.github.mcpejs.notesync.document

import org.springframework.web.bind.annotation.*

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus

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

@RestController
@RequestMapping("/documents")
class DocumentController(private val documentService: DocumentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid
        @RequestBody request: CreateDocumentRequest,
        @RequestAttribute userId: Long
    ): DocumentResponse= DocumentResponse.from(documentService.create(userId, request.title))


    @GetMapping
    fun list(
        @RequestAttribute userId: Long,
    ): List<DocumentResponse> = documentService.findByUserId(userId).map { DocumentResponse.from(it) }
}