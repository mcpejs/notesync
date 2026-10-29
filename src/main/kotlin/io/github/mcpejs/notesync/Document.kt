package io.github.mcpejs.notesync

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "documents")
class Document(
    val title: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,
    val currentVersionId: Long? = null,

    @Column(insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)