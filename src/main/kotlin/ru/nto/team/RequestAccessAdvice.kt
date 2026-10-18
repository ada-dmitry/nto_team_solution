package ru.nto.team

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.http.HttpInputMessage
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter
import java.lang.reflect.Type

/** Apply TASK §6.3 before JSON conversion; the protected admin-key interceptor runs first. */
@ControllerAdvice(assignableTypes = [RequestController::class, AdminRequestController::class])
class RequestAccessAdvice(
    private val staff: StaffService,
    private val requests: PassRequestRepository,
    private val request: HttpServletRequest,
) : RequestBodyAdviceAdapter() {
    override fun supports(parameter: MethodParameter, targetType: Type,
                          converterType: Class<out HttpMessageConverter<*>>): Boolean =
        parameter.parameterType == CreateRequestBody::class.java || parameter.parameterType == DecisionBody::class.java

    override fun beforeBodyRead(inputMessage: HttpInputMessage, parameter: MethodParameter, targetType: Type,
                                converterType: Class<out HttpMessageConverter<*>>): HttpInputMessage {
        checkAccess(parameter)
        return inputMessage
    }

    override fun handleEmptyBody(body: Any?, inputMessage: HttpInputMessage, parameter: MethodParameter,
                                 targetType: Type, converterType: Class<out HttpMessageConverter<*>>): Any? {
        checkAccess(parameter)
        return body
    }

    private fun checkAccess(parameter: MethodParameter) {
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        if (parameter.parameterType == CreateRequestBody::class.java) {
            staff.requireStaff(variables["code"] as String)
        } else {
            val id = (variables["id"] as String).toLong()
            if (!requests.existsById(id)) throw ApiException(404, "Request not found")
        }
    }
}
