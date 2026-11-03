package io.github.mcpejs.notesync.cli

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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

    private fun fail(response: HttpResponse<String>): Nothing =
        throw ApiException(response.statusCode(), "서버 오류 ${response.statusCode()}: ${response.body()}")
}
