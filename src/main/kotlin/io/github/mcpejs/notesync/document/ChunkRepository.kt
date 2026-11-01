package io.github.mcpejs.notesync.document

import org.springframework.data.jpa.repository.JpaRepository

interface ChunkRepository : JpaRepository<Chunk, Long> {
    fun findByUserIdAndContentHashIn(userId: Long, contentHashes: List<ByteArray>): List<Chunk>
}
