package io.github.mcpejs.notesync

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    val name: String,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)