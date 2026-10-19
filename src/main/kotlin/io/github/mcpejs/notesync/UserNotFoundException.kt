package io.github.mcpejs.notesync

class UserNotFoundException(id: Long): RuntimeException("User $id not found")