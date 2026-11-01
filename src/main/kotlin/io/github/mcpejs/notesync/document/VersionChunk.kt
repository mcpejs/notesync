package io.github.mcpejs.notesync.document

import jakarta.persistence.*
import java.io.Serializable

@Embeddable
data class VersionChunkId(
    val versionId: Long,
    val pos: Int,
) : Serializable

@Entity
@Table(name = "version_chunks")
class VersionChunk(
    @EmbeddedId
    val id: VersionChunkId,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id")
    val chunk: Chunk,
)
