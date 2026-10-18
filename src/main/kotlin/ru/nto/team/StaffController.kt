package ru.nto.team

import org.springframework.web.bind.annotation.*

// TODO: complete auth API behavior; this compilable route delegates to an unfinished service.
@RestController
class StaffController(private val service: StaffService) {
    @GetMapping("/api/{code}/auth")
    fun profile(@PathVariable code: String) = service.profile(code)
}
