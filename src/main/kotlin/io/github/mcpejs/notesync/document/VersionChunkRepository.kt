package io.github.mcpejs.notesync.document

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface VersionChunkRepository : JpaRepository<VersionChunk, VersionChunkId> {
    @Query("select c.content from VersionChunk vc join vc.chunk c where vc.id.versionId = :versionId order by vc.id.pos")
    fun findContentsInOrder(versionId: Long): List<ByteArray>
}
