package io.github.mcpejs.notesync.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SyncStateTest {
    @TempDir lateinit var folder: Path

    @Test
    fun stateSurvivesSaveAndLoad() {
        val state = mapOf("a.md" to FileState(1, null, "h"), "b.md" to FileState(2, 7, "i"))
        saveState(folder, state)

        assertEquals(state, loadState(folder).toMap())
    }

    @Test
    fun missingStateIsEmpty() {
        assertEquals(emptyMap(), loadState(folder).toMap())
    }

    @Test
    fun sameInputGivesSameKey() {
        assertEquals(idempotencyKey(1, 2, "h"), idempotencyKey(1, 2, "h"))
    }

    @Test
    fun revertedContentOnNewBaseGetsNewKey() {
        assertNotEquals(idempotencyKey(1, 2, "h"), idempotencyKey(1, 3, "h"))
    }

    @Test
    fun sameContentInOtherDocumentGetsNewKey() {
        assertNotEquals(idempotencyKey(1, 2, "h"), idempotencyKey(9, 2, "h"))
    }

    @Test
    fun takenNameGetsDocumentId() {
        val state = mapOf("회의록.md" to FileState(1, 2, "h"))

        assertEquals("회의록.md", fileNameFor("회의록", 1, state, folder))
        assertEquals("회의록 (5).md", fileNameFor("회의록", 5, state, folder))
    }

    @Test
    fun untrackedLocalFileIsNotOverwritten() {
        Files.writeString(folder.resolve("메모.md"), "내 파일")

        assertEquals("메모 (5).md", fileNameFor("메모", 5, emptyMap(), folder))
    }

    @Test
    fun forbiddenCharactersAreReplaced() {
        assertEquals("a_b.md", fileNameFor("a/b", 1, emptyMap(), folder))
    }

    @Test
    fun trackedDocumentKeepsItsName() {
        val state = mapOf("회의록 (5).md" to FileState(5, 2, "h"))

        assertEquals("회의록 (5).md", fileNameFor("회의록", 5, state, folder))
    }

    @Test
    fun suffixedNameInUseIsNotOverwritten() {
        Files.writeString(folder.resolve("메모.md"), "내 파일")
        Files.writeString(folder.resolve("메모 (5).md"), "내 다른 파일")

        assertEquals("메모 (5) 2.md", fileNameFor("메모", 5, emptyMap(), folder))
    }

    @Test
    fun namesDifferingOnlyInCaseDoNotShareAFile() {
        val state = mapOf("memo.md" to FileState(1, 2, "h"))

        assertEquals("MEMO (5).md", fileNameFor("MEMO", 5, state, folder))
    }
}
