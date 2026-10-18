package ru.nto.team

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StaffService(private val staff: StaffRepository) {
    fun requireStaff(code: String): Staff {
        if (!Regex("[A-Za-z0-9]{4}").matches(code)) throw ApiException(400, "Invalid code format")
        return staff.findByCode(code) ?: throw ApiException(401, "Unauthorized")
    }

    fun profile(code: String): ProfileDto = ProfileDto(requireStaff(code).fullName)
}
