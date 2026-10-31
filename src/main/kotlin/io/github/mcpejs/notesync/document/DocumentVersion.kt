package io.github.mcpejs.notesync.document

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "versions")
class DocumentVersion(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    val document: Document,

    val num: Int,

    val clientRequestId: String,

    @Column(insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)
