package io.github.mcpejs.notesync.common

import io.github.mcpejs.notesync.auth.InvalidCredentialsException
import io.github.mcpejs.notesync.auth.UnauthorizedException
import io.github.mcpejs.notesync.document.ConflictCopyCreatedException
import io.github.mcpejs.notesync.document.DocumentNotFoundException
import io.github.mcpejs.notesync.document.TooManyChunksException
import io.github.mcpejs.notesync.user.UserNotFoundException

import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.ErrorResponse as SpringErrorResponse
import org.springframework.dao.DataIntegrityViolationException

data class ErrorResponse(val code: String, val message: String)

data class VersionConflictResponse(
    val code: String,
    val message: String,
    val currentVersionId: Long?,
    val conflictCopyId: Long,
    val conflictCopyTitle: String,
)

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log=LoggerFactory.getLogger(javaClass)
    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(e: UserNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("User Not Found : {}", e.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("USER_NOT_FOUND", e.message ?: ""))
    }

    @ExceptionHandler(DocumentNotFoundException::class)
    fun handleDocumentNotFound(e: DocumentNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("Document Not Found : {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("DOCUMENT_NOT_FOUND", "문서를 찾을 수 없습니다"))
    }

    @ExceptionHandler(ConflictCopyCreatedException::class)
    fun handleVersionConflict(e: ConflictCopyCreatedException): ResponseEntity<VersionConflictResponse> {
        log.warn("Version conflict, copy : {}", e.copyId)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            VersionConflictResponse(
                "VERSION_CONFLICT",
                "다른 기기에서 먼저 수정되었습니다",
                e.currentVersionId,
                e.copyId,
                e.copyTitle,
            )
        )
    }

    @ExceptionHandler(BodyTooLargeException::class)
    fun handleBodyTooLarge(e: BodyTooLargeException): ResponseEntity<ErrorResponse> {
        log.warn("Body too large")
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
            .body(ErrorResponse("BODY_TOO_LARGE", "요청 본문은 1MB 이하여야 합니다"))
    }

    @ExceptionHandler(TooManyChunksException::class)
    fun handleTooManyChunks(e: TooManyChunksException): ResponseEntity<ErrorResponse> {
        log.warn("{}", e.message)
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
            .body(ErrorResponse("TOO_MANY_CHUNKS", "문단이 너무 많습니다"))
    }

    @ExceptionHandler(LengthRequiredException::class)
    fun handleLengthRequired(e: LengthRequiredException): ResponseEntity<ErrorResponse> {
        log.warn("Content-Length missing")
        return ResponseEntity.status(HttpStatus.LENGTH_REQUIRED)
            .body(ErrorResponse("LENGTH_REQUIRED", "Content-Length 헤더가 필요합니다"))
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(e: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        log.warn("Login failed")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다"))
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(e: UnauthorizedException): ResponseEntity<ErrorResponse> {
        log.warn("Unauthorized : {}", e.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("UNAUTHORIZED", "인증이 필요합니다"))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException):ResponseEntity<ErrorResponse>{
        log.warn("DataIntegrityViolationException : {}", e.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("DATA_CONFLICT", "중복 되는 정보가 존재합니다."))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.warn("Unreadable : {}", e.message)
        return ResponseEntity.badRequest().body(ErrorResponse("INVALID_JSON", "요청 형식이 올바르지 않습니다"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message= e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed : {}", message)
        return ResponseEntity.badRequest().body(ErrorResponse("INVALID_INPUT", message))
    }

    @ExceptionHandler(TypeMismatchException::class)
    fun handleTypeMismatch(e: TypeMismatchException): ResponseEntity<ErrorResponse> {
        log.warn("Type mismatch : {}", e.message)
        return ResponseEntity.badRequest().body(ErrorResponse("INVALID_INPUT", "${e.propertyName ?: "값"}의 형식이 올바르지 않습니다."))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        if(e is SpringErrorResponse){
            val status = e.statusCode
            log.warn("{} : {}", status, e.message)
            return ResponseEntity.status(status).body(ErrorResponse("HTTP_${status.value()}", e.body.detail ?: ""))
        }
        log.error("Unexpected error", e)
        return ResponseEntity.internalServerError().body(ErrorResponse("INTERNAL_ERROR", "서버 오류가 발생했습니다"))
    }
}