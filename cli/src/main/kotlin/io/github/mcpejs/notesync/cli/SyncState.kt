package io.github.mcpejs.notesync.cli

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

// versionId는 다음 저장의 baseVersionId, hash는 마지막 동기화 때 내용의 해시
@JsonIgnoreProperties(ignoreUnknown = true)
data class FileState(val docId: Long, val versionId: Long?, val hash: String)

private val json = jacksonObjectMapper()

fun loadState(folder: Path): MutableMap<String, FileState> {
    val file = folder.resolve(".notesync/state.json")
    if (!Files.exists(file)) return mutableMapOf()
    return json.readValue(Files.readString(file))
}

// 임시 파일에 쓰고 옮긴다. 쓰다가 죽어도 state.json이 반쯤 쓰인 채로 남지 않는다
fun saveState(folder: Path, state: Map<String, FileState>) {
    val dir = Files.createDirectories(folder.resolve(".notesync"))
    val temp = dir.resolve("state.json.tmp")
    Files.writeString(temp, json.writeValueAsString(state))
    Files.move(temp, dir.resolve("state.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

fun contentHash(content: String): String = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(content.toByteArray(StandardCharsets.UTF_8))
)

// 같은 입력이면 같은 키가 나와서 재시도 때 키를 저장해 둘 필요가 없다.
// base가 없으면 내용을 되돌린 저장이 옛 저장의 재시도로 처리된다
fun idempotencyKey(docId: Long, baseVersionId: Long?, hash: String): String =
    UUID.nameUUIDFromBytes("$docId:$baseVersionId:$hash".toByteArray(StandardCharsets.UTF_8)).toString()

// 제목은 서버에서 겹칠 수 있다. 다른 문서나 추적하지 않는 파일이 쓰는 이름이면 문서 ID를 붙인다.
// 이미 추적 중인 문서는 쓰던 이름을 그대로 쓴다. 충돌 저장을 재시도할 때 파일이 늘어나면 안 된다
fun fileNameFor(title: String, docId: Long, state: Map<String, FileState>, folder: Path): String {
    state.entries.find { it.value.docId == docId }?.let { return it.key }

    val safeTitle = title.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_")
    val isFree = { name: String ->
        state.keys.none { it.equals(name, ignoreCase = true) } && !Files.exists(folder.resolve(name))
    }
    return (sequenceOf("$safeTitle.md", "$safeTitle ($docId).md") + generateSequence(2) { it + 1 }.map { "$safeTitle ($docId) $it.md" })
        .first(isFree)
}
