package io.github.mcpejs.notesync.common

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

const val MAX_BODY_BYTES = 1024 * 1024L

class BodyTooLargeException : RuntimeException("Request body too large")

class LengthRequiredException : RuntimeException("Content-Length required")

// 본문을 읽기 전에 막는다. 길이를 밝히지 않는 chunked 요청은 크기를 미리 알 수 없어 거절한다
@Component
class BodySizeInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val length = request.contentLengthLong
        if (length < 0) throw LengthRequiredException()
        if (length > MAX_BODY_BYTES) throw BodyTooLargeException()
        return true
    }
}
