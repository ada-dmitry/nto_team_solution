package ru.nto.team

data class CreateRequestBody(
    val fullName: String? = null,
    val phone: String? = null,
    val organization: String? = null,
    val purpose: String? = null,
    val validFrom: String? = null,
    val validTo: String? = null,
)

data class DecisionBody(val status: String? = null, val comment: String? = null)
