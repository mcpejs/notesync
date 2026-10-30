package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.user.UserService

import org.springframework.stereotype.Service

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val userService: UserService,
) {
    companion object{
        const val DEFAULT_TITLE = "제목없음"
    }

    fun create(userId: Long, title: String?): Document {
        val user = userService.findById(userId)
        val document = Document(if(title.isNullOrBlank()) DEFAULT_TITLE else title, user)
        return documentRepository.save(document)
    }

    fun findByUserId(userId: Long): List<Document> {
        userService.findById(userId)
        val documents = documentRepository.findByUserId(userId)
        return documents
    }
}
