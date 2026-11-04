package io.github.mcpejs.notesync.document

class TooManyChunksException(val count: Int) : RuntimeException("Too many chunks: $count")
