package io.github.mcpejs.notesync

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

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(e: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        log.warn("Login failed")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다"))
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