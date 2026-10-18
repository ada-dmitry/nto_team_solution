package ru.nto.team

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

data class ValidatedRequest(
    val fullName: String,
    val phone: String,
    val organization: String?,
    val purpose: String,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime,
)

data class ValidatedDecision(val status: RequestStatus, val comment: String?)

object RequestValidation {
    private val datePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}")
    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)

    private fun parseDate(value: String?): LocalDateTime? {
        if (value == null || !datePattern.matches(value)) return null
        return try { LocalDateTime.parse(value, formatter) } catch (_: DateTimeParseException) { null }
    }

    fun create(body: CreateRequestBody, now: LocalDateTime): ValidatedRequest {
        val errors = linkedMapOf<String, String>()
        val fullName = body.fullName?.trim().orEmpty()
        val phone = body.phone?.trim().orEmpty()
        val organization = body.organization?.trim()?.takeIf { it.isNotEmpty() }
        val purpose = body.purpose?.trim().orEmpty()
        val from = parseDate(body.validFrom?.trim())
        val to = parseDate(body.validTo?.trim())
        if (fullName.length !in 5..150) errors["fullName"] = "Укажите ФИО длиной от 5 до 150 символов"
        if (!Regex("\\+7[0-9]{10}").matches(phone)) errors["phone"] = "Укажите телефон в формате +7XXXXXXXXXX"
        if (organization != null && organization.length > 150) errors["organization"] = "Не более 150 символов"
        if (purpose.length !in 5..200) errors["purpose"] = "Укажите цель длиной от 5 до 200 символов"
        if (from == null || from < now) errors["validFrom"] = "Укажите существующую дату не раньше текущего времени"
        if (to == null) errors["validTo"] = "Укажите существующую дату в формате yyyy-MM-dd'T'HH:mm:ss"
        else if (from != null && (to <= from || to.toLocalDate() != from.toLocalDate())) {
            errors["validTo"] = "Окончание должно быть позже начала в тот же день"
        }
        if (errors.isNotEmpty()) throw FieldValidationException(errors)
        return ValidatedRequest(fullName, phone, organization, purpose, from!!, to!!)
    }

    fun decision(body: DecisionBody): ValidatedDecision {
        val errors = linkedMapOf<String, String>()
        val status = when (body.status?.trim()) {
            "APPROVED" -> RequestStatus.APPROVED
            "REJECTED" -> RequestStatus.REJECTED
            else -> null
        }
        if (status == null) errors["status"] = "Укажите APPROVED или REJECTED"
        val comment = if (status == RequestStatus.REJECTED) body.comment?.trim() else null
        if (status == RequestStatus.REJECTED && (comment == null || comment.length !in 1..500)) {
            errors["comment"] = "Укажите комментарий длиной от 1 до 500 символов"
        }
        if (errors.isNotEmpty()) throw FieldValidationException(errors)
        return ValidatedDecision(status!!, comment)
    }
}
