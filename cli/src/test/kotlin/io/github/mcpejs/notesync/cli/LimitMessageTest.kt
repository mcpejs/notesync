package io.github.mcpejs.notesync.cli

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// 413 본문을 사람이 읽을 메시지로 바꾸는지 본다
class LimitMessageTest {
    private val server = FakeServer()

    @AfterEach
    fun stop() = server.close()

    private fun failWith(body: Map<String, Any?>): String {
        val document = server.create("문서")
        server.failingDocumentId = document.id
        server.failingBody = body
        val error = assertFailsWith<ApiException> {
            ApiClient(server.baseUrl, "token").saveVersion(document.id, null, "내용", "00000000-0000-0000-0000-000000000000")
        }
        assertEquals(413, error.status)
        return error.message!!
    }

    @Test
    fun bodyTooLargeShowsSizesAndPercent() {
        val message = failWith(mapOf("code" to "BODY_TOO_LARGE", "limit" to 1_048_576, "actual" to 1_153_434))

        assertEquals(
            "요청 크기가 제한을 넘었습니다 (요청 1,153,434바이트, 제한 1,048,576바이트, 10.0% 초과). " +
                "제한은 파일 크기가 아니라 JSON으로 보낸 요청 크기 기준이라, 줄바꿈과 따옴표가 늘어나 1MB보다 작은 파일도 넘을 수 있습니다. " +
                "로컬 파일은 그대로 있으니 줄인 뒤 다시 push하세요.",
            message,
        )
    }

    @Test
    fun tooManyChunksShowsCountsAndPercent() {
        val message = failWith(mapOf("code" to "TOO_MANY_CHUNKS", "limit" to 4_096, "actual" to 5_000))

        assertEquals(
            "문단(빈 줄로 나뉜 덩어리) 수가 제한을 넘었습니다 (문단 5,000개, 제한 4,096개, 22.1% 초과). " +
                "로컬 파일은 그대로 있으니 줄인 뒤 다시 push하세요.",
            message,
        )
    }

    @Test
    fun oneByteOverShowsLessThanPointOnePercent() {
        val message = failWith(mapOf("code" to "BODY_TOO_LARGE", "limit" to 1_048_576, "actual" to 1_048_577))

        assertEquals(true, message.contains("(요청 1,048,577바이트, 제한 1,048,576바이트, 0.1% 미만 초과)"), message)
    }

    @Test
    fun oldServerWithoutFiguresShowsBaseMessage() {
        val message = failWith(mapOf("code" to "TOO_MANY_CHUNKS"))

        assertEquals("문단(빈 줄로 나뉜 덩어리) 수가 제한을 넘었습니다. 로컬 파일은 그대로 있으니 줄인 뒤 다시 push하세요.", message)
    }

    @Test
    fun nonNumericFiguresShowBaseMessage() {
        val message = failWith(mapOf("code" to "BODY_TOO_LARGE", "limit" to "1MB", "actual" to null))

        assertEquals(false, message.contains("%"), message)
        assertEquals(true, message.startsWith("요청 크기가 제한을 넘었습니다. "), message)
    }

    @Test
    fun unknownCodeStillTellsFileIsKept() {
        val message = failWith(mapOf("code" to "SOMETHING_ELSE"))

        assertEquals("서버가 요청이 너무 크다고 거절했습니다 (413). 로컬 파일은 그대로 있으니 줄인 뒤 다시 push하세요.", message)
    }
}
