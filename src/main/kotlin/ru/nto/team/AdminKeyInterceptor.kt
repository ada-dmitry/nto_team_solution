package ru.nto.team

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminKeyInterceptor(@Value("\${ADMIN_KEY:teaching-admin}") private val adminKey: String) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (adminKey.isNotEmpty() && request.getHeader("X-Admin-Key") == adminKey) return true
        response.status = 403
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.writer.write("{\"error\":\"Forbidden\"}")
        return false
    }
}
