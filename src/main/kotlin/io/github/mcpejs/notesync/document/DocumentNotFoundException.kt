package io.github.mcpejs.notesync.document

class DocumentNotFoundException(id: Long) : RuntimeException("Document $id not found")
