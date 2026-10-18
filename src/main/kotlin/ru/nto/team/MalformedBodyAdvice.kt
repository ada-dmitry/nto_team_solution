package ru.nto.team

import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class MalformedBodyAdvice {
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformed(): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to "Invalid request body"))
}
