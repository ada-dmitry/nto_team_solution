package ru.nto.team

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class RequestValidationTest : IntegrationTestSupport() {
    private fun errors(vararg values: Pair<String, Any?>): Set<String> {
        val before = snapshot()
        val (status, json) = request("POST", "/api/Ab12/requests", body(*values))
        assertEquals(400, status, json.toString())
        assertEquals(setOf("errors"), json.fieldNames().asSequence().toSet())
        assertTrue(json["errors"].all { it.isTextual && it.asText().isNotBlank() })
        assertEquals(before, snapshot())
        return json["errors"].fieldNames().asSequence().toSet()
    }

    @Test fun `collects all errors including missing null and blank required fields`() {
        assertEquals(setOf("fullName", "phone", "organization", "purpose", "validFrom", "validTo"), errors(
            "fullName" to null, "phone" to "bad", "organization" to "x".repeat(151), "purpose" to "  ",
            "validFrom" to "invalid", "validTo" to null))
        val response = request("POST", "/api/Ab12/requests", "{}")
        assertEquals(400, response.first)
        assertEquals(setOf("fullName", "phone", "purpose", "validFrom", "validTo"), response.second["errors"].fieldNames().asSequence().toSet())
    }

    @Test fun `string lengths and ASCII phone rules are checked after trimming`() {
        for ((field, invalid) in listOf("fullName" to "abcd", "fullName" to "x".repeat(151),
            "purpose" to "abcd", "purpose" to "x".repeat(201), "organization" to "x".repeat(151))) {
            assertEquals(setOf(field), errors(field to invalid))
        }
        for (phone in listOf("79991234567", "+7999123456", "+799912345678", "+7 9991234567", "+7999-1234567", "+7９９９１２３４５６７")) {
            assertEquals(setOf("phone"), errors("phone" to phone))
        }
    }

    @Test fun `strict date grammar rejects impossible dates missing seconds offsets and fractions`() {
        for (date in listOf("2026-02-30T10:00:00", "2026-10-14T24:00:00", "2026-10-14T10:00",
            "2026-10-14 10:00:00", "2026-1-14T10:00:00", "2026-10-14T10:00:00Z",
            "2026-10-14T10:00:00.000", "+2026-10-14T10:00:00", "2026-10-14T10:00:60")) {
            assertEquals(setOf("validFrom"), errors("validFrom" to date), date)
            assertEquals(setOf("validTo"), errors("validTo" to date), date)
        }
        assertEquals(setOf("validTo"), errors("validTo" to "2026-10-14T10:00:00"))
        assertEquals(setOf("validTo"), errors("validTo" to "2026-10-14T09:59:59"))
        assertEquals(setOf("validTo"), errors("validTo" to "2026-10-15T00:00:00"))
        assertEquals(setOf("validFrom"), errors("validFrom" to "2026-10-14T08:59:59"))
    }

    @Test fun `exact clock including fractions is used in Moscow regardless of clock zone`() {
        clock.zoneId = java.time.ZoneOffset.UTC
        clock.current = Instant.parse("2026-10-14T06:00:00.789Z")
        assertEquals(setOf("validFrom"), errors("validFrom" to "2026-10-14T09:00:00"))
        assertEquals(201, request("POST", "/api/Ab12/requests", body("validFrom" to "2026-10-14T09:00:01")).first)
        jdbc.execute("DELETE FROM pass_request")
        clock.current = Instant.parse("2026-10-14T06:00:00Z")
        assertEquals(201, request("POST", "/api/Ab12/requests", body("validFrom" to "2026-10-14T09:00:00")).first)
    }

    @Test fun `minimum maximum and trimmed values are accepted and normalized`() {
        val (status, result) = request("POST", "/api/Ab12/requests", body("fullName" to " abcde ",
            "phone" to " +79991234567 ", "purpose" to " ${"x".repeat(200)} ",
            "organization" to " ${"y".repeat(150)} ", "validFrom" to " 2026-10-14T10:00:00 "))
        assertEquals(201, status)
        assertEquals("abcde", result["fullName"].asText())
        assertEquals("+79991234567", result["phone"].asText())
        assertEquals("x".repeat(200), result["purpose"].asText())
        assertEquals("y".repeat(150), result["organization"].asText())
        assertEquals(201, request("POST", "/api/Ab12/requests", body("fullName" to "x".repeat(150),
            "purpose" to "abcde", "phone" to "+79991234568")).first)
    }
}
