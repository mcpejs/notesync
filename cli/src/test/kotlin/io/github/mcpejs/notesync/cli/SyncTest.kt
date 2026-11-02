package io.github.mcpejs.notesync.cli

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 테스트 이름의 P, U 번호는 pull, push 상태표의 ID
class SyncTest {
    @TempDir lateinit var folder: Path

    private val server = FakeServer()

    @AfterEach
    fun stop() = server.close()

    private fun sync() = Sync(ApiClient(server.baseUrl, "token"), folder)
    private fun write(name: String, content: String) = Files.writeString(folder.resolve(name), content)
    private fun read(name: String): String = Files.readString(folder.resolve(name))
    private fun exists(name: String) = Files.exists(folder.resolve(name))
    private fun fileCount() = Files.list(folder).use { files -> files.filter { Files.isRegularFile(it) }.count() }
    private fun state(name: String) = loadState(folder)[name]

    private fun track(name: String, docId: Long, versionId: Long?, hash: String) =
        saveState(folder, loadState(folder) + (name to FileState(docId, versionId, hash)))

    private fun tracked(name: String, document: FakeDocument, content: String, seenId: Long? = document.current) {
        write(name, content)
        track(name, document.id, seenId, contentHash(content))
    }

    @Test
    fun p1NewDocumentWithoutVersionBecomesEmptyFile() {
        val document = server.create("회의록")

        assertTrue(sync().pull())

        assertEquals("", read("회의록.md"))
        assertEquals(FileState(document.id, null, contentHash("")), state("회의록.md"))
    }

    @Test
    fun p2NewDocumentIsDownloaded() {
        val document = server.document("회의록", "내용")

        assertTrue(sync().pull())

        assertEquals("내용", read("회의록.md"))
        assertEquals(FileState(document.id, document.current, contentHash("내용")), state("회의록.md"))
    }

    @Test
    fun p3UntrackedFileIsKeptForDocumentWithoutVersion() {
        val document = server.create("회의록")
        write("회의록.md", "내 파일")

        assertTrue(sync().pull())

        assertEquals("내 파일", read("회의록.md"))
        assertEquals("", read("회의록 (${document.id}).md"))
    }

    @Test
    fun p4UntrackedFileIsKeptForDocumentWithVersion() {
        val document = server.document("회의록", "서버")
        write("회의록.md", "내 파일")

        assertTrue(sync().pull())

        assertEquals("내 파일", read("회의록.md"))
        assertEquals("서버", read("회의록 (${document.id}).md"))
    }

    @Test
    fun p5DeletedFileWithoutVersionIsRestoredEmpty() {
        val document = server.create("회의록")
        track("회의록.md", document.id, null, contentHash(""))

        assertTrue(sync().pull())

        assertEquals("", read("회의록.md"))
    }

    @Test
    fun p6DeletedFileAtSameVersionIsRestored() {
        val document = server.document("회의록", "내용")
        track("회의록.md", document.id, document.current, contentHash("내용"))

        assertTrue(sync().pull())

        assertEquals("내용", read("회의록.md"))
    }

    @Test
    fun p7DeletedFileNeverSyncedIsRestoredToLatest() {
        val document = server.document("회의록", "내용")
        track("회의록.md", document.id, null, "")

        assertTrue(sync().pull())

        assertEquals("내용", read("회의록.md"))
        assertEquals(document.current, state("회의록.md")?.versionId)
    }

    @Test
    fun p8DeletedFileBehindServerIsRestoredToLatest() {
        val document = server.document("회의록", "옛 내용")
        val old = document.current
        server.addVersion(document, "새 내용")
        track("회의록.md", document.id, old, contentHash("옛 내용"))

        assertTrue(sync().pull())

        assertEquals("새 내용", read("회의록.md"))
    }

    @Test
    fun p9NoVersionAndUnchangedDoesNothing() {
        val document = server.create("회의록")
        tracked("회의록.md", document, "")

        assertTrue(sync().pull())

        assertEquals(FileState(document.id, null, contentHash("")), state("회의록.md"))
    }

    @Test
    fun p10NoVersionAndEditedKeepsFile() {
        val document = server.create("회의록")
        tracked("회의록.md", document, "")
        write("회의록.md", "내 수정")

        assertTrue(sync().pull())

        assertEquals("내 수정", read("회의록.md"))
    }

    @Test
    fun p11SameVersionAndUnchangedDoesNotDownload() {
        val document = server.document("회의록", "내용")
        tracked("회의록.md", document, "내용")
        server.requests = 0

        assertTrue(sync().pull())

        assertEquals(1, server.requests)
    }

    @Test
    fun p12SameVersionAndEditedKeepsFile() {
        val document = server.document("회의록", "내용")
        tracked("회의록.md", document, "내용")
        write("회의록.md", "내 수정")

        assertTrue(sync().pull())

        assertEquals("내 수정", read("회의록.md"))
        assertEquals(contentHash("내용"), state("회의록.md")?.hash)
    }

    @Test
    fun p13NeverSyncedAndUnchangedIsOverwritten() {
        val document = server.document("회의록", "서버")
        tracked("회의록.md", document, "", seenId = null)

        assertTrue(sync().pull())

        assertEquals("서버", read("회의록.md"))
        assertEquals(document.current, state("회의록.md")?.versionId)
    }

    @Test
    fun p14NeverSyncedAndEditedKeepsFile() {
        val document = server.document("회의록", "서버")
        write("회의록.md", "내 내용")
        track("회의록.md", document.id, null, "")

        assertTrue(sync().pull())

        assertEquals("내 내용", read("회의록.md"))
        assertNull(state("회의록.md")?.versionId)
    }

    @Test
    fun p15BehindAndUnchangedIsOverwritten() {
        val document = server.document("회의록", "옛 내용")
        tracked("회의록.md", document, "옛 내용")
        server.addVersion(document, "새 내용")

        assertTrue(sync().pull())

        assertEquals("새 내용", read("회의록.md"))
        assertEquals(document.current, state("회의록.md")?.versionId)
    }

    @Test
    fun p16BehindAndEditedKeepsFile() {
        val document = server.document("회의록", "옛 내용")
        tracked("회의록.md", document, "옛 내용")
        val seen = state("회의록.md")
        server.addVersion(document, "새 내용")
        write("회의록.md", "내 수정")

        assertTrue(sync().pull())

        assertEquals("내 수정", read("회의록.md"))
        assertEquals(seen, state("회의록.md"))
    }

    @Test
    fun p17ServerLostVersionAndFileMissingCreatesNothing() {
        val document = server.create("회의록")
        track("회의록.md", document.id, 99, contentHash("내용"))

        assertFalse(sync().pull())

        assertFalse(exists("회의록.md"))
        assertEquals(99, state("회의록.md")?.versionId)
    }

    @Test
    fun p18ServerLostVersionKeepsUnchangedFile() {
        val document = server.create("회의록")
        tracked("회의록.md", document, "내용", seenId = 99)

        assertFalse(sync().pull())

        assertEquals("내용", read("회의록.md"))
    }

    @Test
    fun p18ServerLostVersionKeepsEditedFile() {
        val document = server.create("회의록")
        tracked("회의록.md", document, "내용", seenId = 99)
        write("회의록.md", "내 수정")

        assertFalse(sync().pull())

        assertEquals("내 수정", read("회의록.md"))
    }

    @Test
    fun p19ServerBehindStateKeepsUnchangedFile() {
        val document = server.document("회의록", "서버")
        tracked("회의록.md", document, "내용", seenId = 99)

        assertFalse(sync().pull())

        assertEquals("내용", read("회의록.md"))
    }

    @Test
    fun p19ServerBehindStateKeepsEditedFile() {
        val document = server.document("회의록", "서버")
        tracked("회의록.md", document, "내용", seenId = 99)
        write("회의록.md", "내 수정")

        assertFalse(sync().pull())

        assertEquals("내 수정", read("회의록.md"))
    }

    @Test
    fun p19ServerBehindStateDoesNotRestoreFile() {
        val document = server.document("회의록", "서버")
        track("회의록.md", document.id, 99, contentHash("내용"))

        assertFalse(sync().pull())

        assertFalse(exists("회의록.md"))
    }

    @Test
    fun p20DocumentMissingOnServerKeepsFileAndState() {
        write("회의록.md", "내용")
        track("회의록.md", 999, 5, contentHash("내용"))
        server.document("다른 문서", "다른 내용")

        assertFalse(sync().pull())

        assertEquals("내용", read("회의록.md"))
        assertEquals(999, state("회의록.md")?.docId)
        assertEquals("다른 내용", read("다른 문서.md"))
    }

    @Test
    fun u1EmptyFolderSendsNothing() {
        assertTrue(sync().push())

        assertEquals(0, server.requests)
    }

    @Test
    fun u2DeletedFileIsNotPropagatedAndPullRestoresIt() {
        val document = server.document("회의록", "내용")
        track("회의록.md", document.id, document.current, contentHash("내용"))

        assertTrue(sync().push())

        assertEquals(0, server.requests)
        assertEquals(1, server.documents.size)

        assertTrue(sync().pull())
        assertEquals("내용", read("회의록.md"))
    }

    @Test
    fun u3NewFileCreatesDocumentAndSaves() {
        write("회의록.md", "내용")

        assertTrue(sync().push())

        val document = server.documents.values.single()
        assertEquals("회의록", document.title)
        assertEquals("내용", document.versions.values.single())
        assertEquals(FileState(document.id, document.current, contentHash("내용")), state("회의록.md"))
    }

    @Test
    fun u3NewEmptyFileAlsoCreatesVersion() {
        write("빈 파일.md", "")

        assertTrue(sync().push())

        assertEquals(listOf(""), server.documents.values.single().versions.values.toList())
    }

    @Test
    fun u4UnchangedFileSendsNothing() {
        val document = server.document("회의록", "내용")
        tracked("회의록.md", document, "내용")
        server.addVersion(document, "서버가 더 새로움")
        server.requests = 0

        assertTrue(sync().push())

        assertEquals(0, server.requests)
        assertEquals("내용", read("회의록.md"))
    }

    @Test
    fun u5FirstVersionIsSaved() {
        val document = server.create("회의록")
        tracked("회의록.md", document, "")
        write("회의록.md", "내용")

        assertTrue(sync().push())

        assertEquals("내용", document.versions.values.single())
        assertEquals(document.current, state("회의록.md")?.versionId)
    }

    @Test
    fun u6EditIsSavedOnTopOfBase() {
        val document = server.document("회의록", "내용")
        tracked("회의록.md", document, "내용")
        write("회의록.md", "내 수정")

        assertTrue(sync().push())

        assertEquals(listOf("내용", "내 수정"), document.versions.values.toList())
        assertEquals(FileState(document.id, document.current, contentHash("내 수정")), state("회의록.md"))
    }

    @Test
    fun u7NeverSyncedFileConflictsWithServerVersion() {
        val document = server.document("회의록", "서버")
        write("회의록.md", "내 내용")
        track("회의록.md", document.id, null, "")

        assertTrue(sync().push())

        assertEquals("서버", read("회의록.md"))
        assertEquals("내 내용", read("회의록(충돌 사본).md"))
        assertEquals(document.current, state("회의록.md")?.versionId)
        assertEquals(server.copies().single().id, state("회의록(충돌 사본).md")?.docId)
    }

    @Test
    fun u8StaleFileConflictKeepsBothAndStops() {
        val document = server.document("회의록", "옛 내용")
        tracked("회의록.md", document, "옛 내용")
        server.addVersion(document, "새 내용")
        write("회의록.md", "내 수정")

        assertTrue(sync().push())

        assertEquals("새 내용", read("회의록.md"))
        assertEquals("내 수정", read("회의록(충돌 사본).md"))
        val copy = server.copies().single()
        assertEquals(copy.current, state("회의록(충돌 사본).md")?.versionId)

        assertTrue(sync().push())
        assertEquals(1, server.copies().size)
        assertEquals(2, document.versions.size)
    }

    @Test
    fun u9RetryAfterLostResponseUsesFirstResult() {
        val document = server.document("회의록", "내용")
        tracked("회의록.md", document, "내용")
        val before = loadState(folder)
        write("회의록.md", "내 수정")
        assertTrue(sync().push())
        val savedId = document.current

        saveState(folder, before)
        assertTrue(sync().push())

        assertEquals(2, document.versions.size)
        assertEquals(savedId, state("회의록.md")?.versionId)
    }

    @Test
    fun u9RetryOfConflictMakesNoSecondCopy() {
        val document = server.document("회의록", "옛 내용")
        tracked("회의록.md", document, "옛 내용")
        val before = loadState(folder)
        server.addVersion(document, "새 내용")
        write("회의록.md", "내 수정")
        assertTrue(sync().push())

        // 사본을 받은 뒤 원본을 덮기 전에 죽은 경우
        val copyEntry = "회의록(충돌 사본).md" to state("회의록(충돌 사본).md")!!
        saveState(folder, before + copyEntry)
        write("회의록.md", "내 수정")
        write(copyEntry.first, "사본을 고침")
        server.failingDocumentId = copyEntry.second.docId
        assertFalse(sync().push())

        assertEquals(1, server.copies().size)
        assertEquals("새 내용", read("회의록.md"))
        assertEquals("사본을 고침", read(copyEntry.first))
    }

    @Test
    fun u10ServerLostVersionKeepsFileAndCopiesOnce() {
        val document = server.create("회의록")
        tracked("회의록.md", document, "내용", seenId = 99)
        write("회의록.md", "내 수정")

        assertFalse(sync().push())

        assertEquals("내 수정", read("회의록.md"))
        assertEquals("내 수정", read("회의록(충돌 사본).md"))
        assertEquals(99, state("회의록.md")?.versionId)

        assertFalse(sync().push())
        assertEquals(1, server.copies().size)
    }

    @Test
    fun u11MissingDocumentKeepsFileAndContinues() {
        write("가.md", "내 수정")
        track("가.md", 999, 5, contentHash("내용"))
        write("나.md", "다른 파일")

        assertFalse(sync().push())

        assertEquals("내 수정", read("가.md"))
        assertEquals(5, state("가.md")?.versionId)
        assertEquals("다른 파일", server.documents.values.single().versions.values.single())
    }

    @Test
    fun tooLargeFileFailsAloneAndOthersContinue() {
        val big = server.document("가", "내용")
        tracked("가.md", big, "내용")
        write("가.md", "내 수정")
        val other = server.document("나", "내용")
        tracked("나.md", other, "내용")
        write("나.md", "다른 수정")
        server.failingDocumentId = big.id

        assertFalse(sync().push())

        assertEquals("내 수정", read("가.md"))
        assertEquals(big.current, state("가.md")?.versionId)
        assertEquals("다른 수정", other.versions.values.last())
    }

    @Test
    fun nonUtf8FileFailsAloneAndOthersContinue() {
        Files.write(folder.resolve("가.md"), byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        write("나.md", "다른 파일")

        assertFalse(sync().push())

        assertEquals("다른 파일", server.documents.values.single().versions.values.single())
    }

    @Test
    fun nonUtf8FileDuringPullFailsAloneAndOthersContinue() {
        val document = server.document("가", "옛 내용")
        Files.write(folder.resolve("가.md"), byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        track("가.md", document.id, document.current, "")
        server.addVersion(document, "새 내용")
        server.document("나", "다른 내용")

        assertFalse(sync().pull())

        assertEquals("다른 내용", read("나.md"))
    }

    @Test
    fun unauthorizedStopsAtOnce() {
        write("가.md", "내용")
        write("나.md", "내용")
        server.token = "다른 토큰"

        val error = assertFailsWith<ApiException> { sync().push() }

        assertEquals(401, error.status)
        assertEquals(1, server.requests)
    }

    @Test
    fun networkFailureStopsAndKeepsState() {
        val document = server.document("회의록", "내용")
        tracked("회의록.md", document, "내용")
        write("회의록.md", "내 수정")
        val before = loadState(folder)
        server.close()

        assertFailsWith<IOException> { sync().push() }

        assertEquals(before, loadState(folder))
        assertEquals("내 수정", read("회의록.md"))
    }

    @Test
    fun failedFileWriteLeavesStateBehind() {
        val document = server.document("회의록", "옛 내용")
        tracked("회의록.md", document, "옛 내용")
        val before = loadState(folder)
        server.addVersion(document, "새 내용")
        val file = folder.resolve("회의록.md").toFile()
        file.setReadOnly()

        try {
            assertFalse(sync().pull())
        } finally {
            file.setWritable(true)
        }

        assertEquals(before, loadState(folder))
        assertEquals("옛 내용", read("회의록.md"))
    }

    @Test
    fun repeatedPullsChangeNothing() {
        server.document("회의록", "내용")

        assertTrue(sync().pull())
        val before = loadState(folder)
        val files = fileCount()
        assertTrue(sync().pull())

        assertEquals(before, loadState(folder))
        assertEquals(files, fileCount())
    }
}
