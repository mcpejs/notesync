package io.github.mcpejs.notesync.document

class ConflictCopyCreatedException(
    val copyId: Long,
    val copyTitle: String,
    val currentVersionId: Long?,
) : RuntimeException("Version conflict")