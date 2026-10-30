package io.github.mcpejs.notesync.auth

import io.jsonwebtoken.JwtException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthInterceptor(private val jwtProvider: JwtProvider) : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod) return true

        val auth=request.getHeader("Authorization")
        if(auth==null || !auth.startsWith("Bearer ")) throw UnauthorizedException("Missing bearer token")
        val token=auth.removePrefix("Bearer ")

        val userId:Long
        try {
            userId = jwtProvider.parseUserId(token)
        } catch (e: JwtException) {
            throw UnauthorizedException("Token invalid")
        } catch (e: IllegalArgumentException) {
            throw UnauthorizedException("Token invalid")
        }

        request.setAttribute("userId",userId)
        return true
    }
}