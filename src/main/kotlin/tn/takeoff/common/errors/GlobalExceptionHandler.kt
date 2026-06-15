package tn.takeoff.common.errors

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(TakeOffException::class)
    fun handleTakeOff(ex: TakeOffException): ProblemDetail =
        ProblemDetail.forStatus(ex.status).apply {
            type = URI.create("https://takeoff.tn/errors/${ex.code}")
            title = ex.message
            setProperty("code", ex.code)
        }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail =
        ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY).apply {
            type = URI.create("https://takeoff.tn/errors/validation")
            title = "Validation failed"
            setProperty("code", "takeoff.validation")
            setProperty("errors", ex.bindingResult.fieldErrors.map {
                mapOf("field" to it.field, "message" to it.defaultMessage)
            })
        }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail =
        ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY).apply {
            type = URI.create("https://takeoff.tn/errors/validation")
            title = "Constraint violation"
            setProperty("code", "takeoff.validation")
        }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ProblemDetail =
        ProblemDetail.forStatus(HttpStatus.FORBIDDEN).apply {
            type = URI.create("https://takeoff.tn/errors/forbidden")
            title = "Access denied"
            setProperty("code", "takeoff.forbidden")
        }
}
