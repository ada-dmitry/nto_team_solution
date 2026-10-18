package ru.nto.team

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

class ApiException(val status: Int, message: String) : RuntimeException(message)
class FieldValidationException(val errors: Map<String, String>) : RuntimeException("Invalid fields")

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(FieldValidationException::class)
    fun fieldErrors(error: FieldValidationException): ResponseEntity<Map<String, Map<String, String>>> =
        ResponseEntity.badRequest().body(mapOf("errors" to error.errors))

    @ExceptionHandler(ApiException::class)
    fun apiError(error: ApiException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(error.status).body(mapOf("error" to error.message!!))
}
