package ru.nto.team

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface StaffRepository : JpaRepository<Staff, Long> {
    fun findByCode(code: String): Staff?
}

interface PassRequestRepository : JpaRepository<PassRequest, Long> {
    fun findByHostIdOrderByValidFromAscIdAsc(hostId: Long): List<PassRequest>
    fun findByHostIdAndStatusOrderByValidFromAscIdAsc(hostId: Long, status: RequestStatus): List<PassRequest>
    fun findByIdAndHostId(id: Long, hostId: Long): PassRequest?
    fun existsByHostIdAndPhoneAndStatusInAndValidFromLessThanAndValidToGreaterThan(
        hostId: Long, phone: String, statuses: Collection<RequestStatus>,
        validTo: LocalDateTime, validFrom: LocalDateTime,
    ): Boolean
}
