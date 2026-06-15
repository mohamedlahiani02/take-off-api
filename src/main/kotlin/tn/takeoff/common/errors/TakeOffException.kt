package tn.takeoff.common.errors

import org.springframework.http.HttpStatus

open class TakeOffException(
    val code: String,
    override val message: String,
    val status: HttpStatus,
) : RuntimeException(message)

class NotFoundException(resource: String, id: Any) :
    TakeOffException("takeoff.$resource.not_found", "$resource not found: $id", HttpStatus.NOT_FOUND)

class ConflictException(code: String, message: String) :
    TakeOffException(code, message, HttpStatus.CONFLICT)

class UnauthorizedException(code: String = "takeoff.auth.unauthorized", message: String = "Unauthorized") :
    TakeOffException(code, message, HttpStatus.UNAUTHORIZED)

class BadRequestException(code: String, message: String) :
    TakeOffException(code, message, HttpStatus.BAD_REQUEST)
