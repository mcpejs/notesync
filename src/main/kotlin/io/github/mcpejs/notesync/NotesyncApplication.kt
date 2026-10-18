package io.github.mcpejs.notesync

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NotesyncApplication

fun main(args: Array<String>) {
	runApplication<NotesyncApplication>(*args)
}
