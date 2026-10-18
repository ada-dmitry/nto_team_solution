package ru.nto.team

import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus

// TODO: complete request API behavior, responses and errors.
@RestController
@RequestMapping("/api/{code}/requests")
class RequestController(private val service: RequestService) {
    @GetMapping fun list(@PathVariable code: String, @RequestParam(required = false) status: String?) = service.list(code, status)
    @GetMapping("/{id}") fun details(@PathVariable code: String, @PathVariable id: Long) = service.details(code, id)
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable code: String, @RequestBody body: CreateRequestBody) = service.create(code, body)
    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable code: String, @PathVariable id: Long) = service.cancel(code, id)
}
