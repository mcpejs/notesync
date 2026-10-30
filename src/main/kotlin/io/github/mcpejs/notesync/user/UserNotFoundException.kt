package io.github.mcpejs.notesync.user

class UserNotFoundException(id: Long): RuntimeException("User $id not found")