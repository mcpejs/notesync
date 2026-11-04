package io.github.mcpejs.notesync.cli

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.InetSocketAddress
import java.util.UUID

class FakeDocument(val id: Long, val title: String) {
    val versions = linkedMapOf<Long, String>()
    val current: Long? get() = versions.keys.lastOrNull()
}

// 저장 규칙은 실제 서버와 같다. 같은 키는 첫 결과, base가 다르면 사본을 만들고 409
class FakeServer : AutoCloseable {
    private val json = jacksonObjectMapper()
    private val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private var nextId = 1L
    private val keys = mutableMapOf<String, Pair<Long, Long>>()

    val documents = linkedMapOf<Long, FakeDocument>()
    var requests = 0
    var token = "token"
    var failingDocumentId: Long? = null
    var failingBody: Map<String, Any?> = mapOf("code" to "BODY_TOO_LARGE", "limit" to 1_048_576, "actual" to 1_153_434)
    // 409를 돌려주기 직전에 부른다. 응답과 다음 요청 사이에 다른 기기가 사본을 고친 상황을 만든다
    var onConflict: (FakeDocument) -> Unit = {}
    val baseUrl: String

    init {
        http.createContext("/") { exchange -> exchange.use { handle(it) } }
        http.start()
        baseUrl = "http://127.0.0.1:${http.address.port}"
    }

    fun create(title: String): FakeDocument {
        val document = FakeDocument(nextId++, title)
        documents[document.id] = document
        return document
    }

    fun addVersion(document: FakeDocument, content: String): Long {
        val id = nextId++
        document.versions[id] = content
        return id
    }

    fun document(title: String, content: String): FakeDocument = create(title).also { addVersion(it, content) }

    fun copies() = documents.values.filter { it.title.endsWith("(충돌 사본)") }

    private fun handle(exchange: HttpExchange) {
        requests++
        if (exchange.requestHeaders.getFirst("Authorization") != "Bearer $token") return respond(exchange, 401, mapOf("code" to "UNAUTHORIZED"))

        val parts = exchange.requestURI.path.trim('/').split('/')
        val document = parts.getOrNull(1)?.let { documents[it.toLong()] }
        val isPost = exchange.requestMethod == "POST"
        when {
            parts.size == 1 && isPost -> {
                val body = json.readValue<Map<String, Any?>>(exchange.requestBody.readAllBytes())
                respond(exchange, 201, info(create(body["title"] as String)))
            }
            parts.size == 1 -> respond(exchange, 200, documents.values.map { info(it) })
            document == null -> respond(exchange, 404, mapOf("code" to "DOCUMENT_NOT_FOUND"))
            parts.size == 2 -> respond(exchange, 200, info(document))
            isPost -> save(exchange, document)
            else -> respond(exchange, 200, mapOf("content" to document.versions.getValue(parts[3].toLong())))
        }
    }

    private fun save(exchange: HttpExchange, document: FakeDocument) {
        if (document.id == failingDocumentId) return respond(exchange, 413, failingBody)

        val key = UUID.fromString(exchange.requestHeaders.getFirst("Idempotency-Key")).toString()
        val body = json.readValue<Map<String, Any?>>(exchange.requestBody.readAllBytes())
        val content = body["content"] as String
        val base = (body["baseVersionId"] as Number?)?.toLong()

        val first = keys[key]
        if (first != null && first.first != document.id) return respond(exchange, 409, conflict(document, first.first, first.second))
        if (first != null) return respond(exchange, 201, mapOf("id" to first.second))

        if (document.current != base) {
            val copy = create(document.title + "(충돌 사본)")
            val copyVersionId = addVersion(copy, content)
            keys[key] = copy.id to copyVersionId
            val body = conflict(document, copy.id, copyVersionId)
            onConflict(copy)
            return respond(exchange, 409, body)
        }
        val id = addVersion(document, content)
        keys[key] = document.id to id
        respond(exchange, 201, mapOf("id" to id))
    }

    private fun conflict(document: FakeDocument, copyId: Long, copyVersionId: Long) = mapOf(
        "code" to "VERSION_CONFLICT",
        "currentVersionId" to document.current,
        "conflictCopyId" to copyId,
        "conflictCopyTitle" to documents.getValue(copyId).title,
        "conflictCopyVersionId" to copyVersionId,
    )

    private fun info(document: FakeDocument) =
        mapOf("id" to document.id, "title" to document.title, "currentVersionId" to document.current)

    private fun respond(exchange: HttpExchange, status: Int, body: Any) {
        val bytes = json.writeValueAsBytes(body)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    override fun close() = http.stop(0)
}
