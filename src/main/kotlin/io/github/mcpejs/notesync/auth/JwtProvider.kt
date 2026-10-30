package io.github.mcpejs.notesync.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date

@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.expiration-seconds}") val expirationSeconds: Long,
) {
    // secret 문자열 → 바이트 → 서명 키
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun createToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + expirationSeconds*1000)
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun parseUserId(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
        return claims.subject.toLong()
    }
}