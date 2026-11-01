package io.github.mcpejs.notesync.document

import io.github.mcpejs.notesync.user.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "chunks")
class Chunk(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @JdbcTypeCode(SqlTypes.BINARY)
    val contentHash: ByteArray,

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    val content: ByteArray,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
