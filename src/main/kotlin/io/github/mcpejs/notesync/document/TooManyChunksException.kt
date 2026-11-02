package io.github.mcpejs.notesync.document

class TooManyChunksException(count: Int) : RuntimeException("Too many chunks: $count")
