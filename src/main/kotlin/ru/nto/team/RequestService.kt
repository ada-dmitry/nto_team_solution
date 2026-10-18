package ru.nto.team

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Service
class RequestService(private val staff: StaffService, private val requests: PassRequestRepository, private val clock: Clock) {
    @Transactional(readOnly = true)
    fun list(code: String, status: String?): List<RequestListDto> {
        val host = staff.requireStaff(code)
        val filter = status?.let { value ->
            RequestStatus.entries.find { it.name == value } ?: throw ApiException(400, "Invalid status")
        }
        val rows = if (filter == null) requests.findByHostIdOrderByValidFromAscIdAsc(host.id)
            else requests.findByHostIdAndStatusOrderByValidFromAscIdAsc(host.id, filter)
        return rows.map { it.toListDto() }
    }

    @Transactional(readOnly = true)
    fun details(code: String, id: Long): RequestDetailsDto {
        val host = staff.requireStaff(code)
        return owned(id, host.id).toDetailsDto()
    }

    private fun owned(id: Long, hostId: Long): PassRequest =
        requests.findByIdAndHostId(id, hostId) ?: throw ApiException(404, "Request not found")
    @Transactional
    fun create(code: String, body: CreateRequestBody): RequestDetailsDto {
        val host = staff.requireStaff(code)
        val now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Europe/Moscow"))
        val fields = RequestValidation.create(body, now)
        if (requests.existsByHostIdAndPhoneAndStatusInAndValidFromLessThanAndValidToGreaterThan(
                host.id, fields.phone, listOf(RequestStatus.PENDING, RequestStatus.APPROVED), fields.validTo, fields.validFrom)) {
            throw ApiException(409, "Duplicate request")
        }
        val timestamp = now.truncatedTo(ChronoUnit.SECONDS)
        return requests.save(PassRequest(host = host, fullName = fields.fullName, phone = fields.phone,
            organization = fields.organization, purpose = fields.purpose, validFrom = fields.validFrom,
            validTo = fields.validTo, status = RequestStatus.PENDING, comment = null,
            createdAt = timestamp, updatedAt = timestamp)).toDetailsDto()
    }
    @Transactional
    fun cancel(code: String, id: Long): RequestDetailsDto {
        val host = staff.requireStaff(code)
        val entity = owned(id, host.id)
        requirePending(entity)
        entity.status = RequestStatus.CANCELLED
        entity.updatedAt = timestamp()
        return entity.toDetailsDto()
    }

    @Transactional
    fun decide(id: Long, body: DecisionBody): RequestDetailsDto {
        val entity = requests.findById(id).orElseThrow { ApiException(404, "Request not found") }
        val fields = RequestValidation.decision(body)
        requirePending(entity)
        entity.status = fields.status
        entity.comment = fields.comment
        entity.updatedAt = timestamp()
        return entity.toDetailsDto()
    }

    private fun requirePending(entity: PassRequest) {
        if (entity.status != RequestStatus.PENDING) throw ApiException(409, "Invalid status transition")
    }

    private fun timestamp(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Europe/Moscow")).truncatedTo(ChronoUnit.SECONDS)
}
