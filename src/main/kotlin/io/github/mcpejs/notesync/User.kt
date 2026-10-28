package io.github.mcpejs.notesync

import jakarta.persistence.*

@Entity
@Table(name = "users")
class User(
    val name: String,
    val email: String,
    val passwordHash: String,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)