package io.github.mcpejs.notesync.cli

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Locale

@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteDocument(val id: Long, val title: String, val currentVersionId: Long?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class VersionBody(val id: Long)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ContentBody(val content: String)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ConflictBody(
    val currentVersionId: Long?,
    val conflictCopyId: Long,
    val conflictCopyTitle: String,
    val conflictCopyVersionId: Long,
)

// 401은 토큰 문제라 어떤 파일을 보내도 실패하므로 바로 멈춘다. 나머지는 그 파일만 실패로 처리한다
class ApiException(val status: Int, message: String) : RuntimeException(message) {
    val fatal: Boolean get() = status == 401
}

sealed interface SaveResult
data class Saved(val versionId: Long) : SaveResult
data class Conflict(val currentVersionId: Long?, val copyId: Long, val copyTitle: String, val copyVersionId: Long) : SaveResult

class ApiClient(private val baseUrl: String, private val token: String) {
    private val http = HttpClient.newHttpClient()
    private val json = jacksonObjectMapper()

    fun listDocuments(): List<RemoteDocument> = json.readValue(expect200(send(get("/documents"))))

    fun getContent(documentId: Long, versionId: Long): String =
        json.readValue<ContentBody>(expect200(send(get("/documents/$documentId/versions/$versionId")))).content

    fun createDocument(title: String): RemoteDocument {
        val response = send(post("/documents", mapOf("title" to title)).build())
        if (response.statusCode() != 201) fail(response)
        return json.readValue(response.body())
    }

    fun saveVersion(documentId: Long, baseVersionId: Long?, content: String, key: String): SaveResult {
        val body = mapOf("baseVersionId" to baseVersionId, "content" to content)
        val request = post("/documents/$documentId/versions", body).header("Idempotency-Key", key).build()
        val response = send(request)
        return when (response.statusCode()) {
            201 -> Saved(json.readValue<VersionBody>(response.body()).id)
            409 -> json.readValue<ConflictBody>(response.body()).let { Conflict(it.currentVersionId, it.conflictCopyId, it.conflictCopyTitle, it.conflictCopyVersionId) }
            else -> fail(response)
        }
    }

    private fun get(path: String): HttpRequest = request(path).GET().build()

    private fun post(path: String, body: Any): HttpRequest.Builder = request(path)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))

    private fun request(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Authorization", "Bearer $token")

    private fun send(request: HttpRequest): HttpResponse<String> =
        http.send(request, HttpResponse.BodyHandlers.ofString())

    private fun expect200(response: HttpResponse<String>): String {
        if (response.statusCode() != 200) fail(response)
        return response.body()
    }

    private fun fail(response: HttpResponse<String>): Nothing {
        val message = if (response.statusCode() == 413) limitExceededMessage(response.body())
            else "서버 오류 ${response.statusCode()}: ${response.body()}"
        throw ApiException(response.statusCode(), message)
    }

    // 413 본문의 code에 따라 사람이 읽을 문장으로 바꾼다. limit, actual이 없는 옛 서버면 수치를 뺀다
    private fun limitExceededMessage(body: String): String {
        val fields = runCatching { json.readValue<Map<String, Any?>>(body) }.getOrDefault(emptyMap())
        val limit = (fields["limit"] as? Number)?.toLong()
        val actual = (fields["actual"] as? Number)?.toLong()
        val retry = "로컬 파일은 그대로 있으니 줄인 뒤 다시 push하세요."
        return when (fields["code"]) {
            "BODY_TOO_LARGE" -> "요청 크기가 제한을 넘었습니다${figures("요청", "바이트", limit, actual)}. " +
                "제한은 파일 크기가 아니라 JSON으로 보낸 요청 크기 기준이라, 줄바꿈과 따옴표가 늘어나 1MB보다 작은 파일도 넘을 수 있습니다. $retry"
            "TOO_MANY_CHUNKS" -> "문단(빈 줄로 나뉜 덩어리) 수가 제한을 넘었습니다${figures("문단", "개", limit, actual)}. $retry"
            else -> "서버가 요청이 너무 크다고 거절했습니다 (413). $retry"
        }
    }

    private fun figures(name: String, unit: String, limit: Long?, actual: Long?): String {
        if (limit == null || actual == null || limit <= 0) return ""
        val percent = (actual - limit) * 100.0 / limit
        val over = if (percent < 0.1) "0.1% 미만" else String.format(Locale.ROOT, "%.1f%%", percent)
        return String.format(Locale.ROOT, " (%s %,d%s, 제한 %,d%s, %s 초과)", name, actual, unit, limit, unit, over)
    }
}
