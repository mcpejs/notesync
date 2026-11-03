package io.github.mcpejs.notesync.document

class ConflictCopyCreatedException(
    val copyId: Long,
    val copyTitle: String,
    val copyVersionId: Long,
    val currentVersionId: Long?,
) : RuntimeException("Version conflict")