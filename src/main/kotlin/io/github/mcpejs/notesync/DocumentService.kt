package io.github.mcpejs.notesync

import org.springframework.stereotype.Service

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val userService: UserService,
) {
    fun create(userId: Long, title: String): Document {
        val user = userService.findById(userId)
        val document = Document(title, user)
        return documentRepository.save(document)
    }

    fun findByUserId(userId: Long): List<Document> {
        userService.findById(userId)
        val documents = documentRepository.findByUserId(userId)
        return documents
    }
}
