package ru.nto.team

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class TransitionApiIntegrationTest : IntegrationTestSupport() {
    private fun decision(id: Long, body: String, key: String? = "test-admin") =
        request("PATCH", "/api/admin/requests/$id", body, key)

    @Test fun `author cancellation commits status keeps creation and truncates updated time`() {
        val id = seed()
        clock.current = Instant.parse("2026-10-14T06:12:34.999Z")
        val (status, result) = request("POST", "/api/Ab12/requests/$id/cancel")
        assertEquals(200, status)
        assertEquals("CANCELLED", result["status"].asText())
        assertTrue(result["comment"].isNull)
        assertEquals("2026-10-13T09:00:00", result["createdAt"].asText())
        assertEquals("2026-10-14T09:12:34", result["updatedAt"].asText())
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM pass_request WHERE id=?", String::class.java, id))
        val before = snapshot()
        val repeated = request("POST", "/api/Ab12/requests/$id/cancel")
        assertEquals(409, repeated.first)
        assertEquals("Invalid status transition", repeated.second["error"].asText())
        assertEquals(before, snapshot())
    }

    @Test fun `approval ignores comment rejection trims it and both commit timestamps`() {
        val approved = seed()
        jdbc.update("UPDATE pass_request SET comment='Старый комментарий' WHERE id=?", approved)
        val result = decision(approved, """{"status":" APPROVED ","comment":"${"x".repeat(501)}"}""")
        assertEquals(200, result.first)
        assertEquals("APPROVED", result.second["status"].asText())
        assertTrue(result.second["comment"].isNull)
        assertNull(jdbc.queryForObject("SELECT comment FROM pass_request WHERE id=?", String::class.java, approved))
        val rejected = seed()
        clock.current = Instant.parse("2026-10-14T07:00:01.123Z")
        val rejection = decision(rejected, """{"status":" REJECTED ","comment":"  Нет пропуска  "}""")
        assertEquals(200, rejection.first)
        assertEquals("REJECTED", rejection.second["status"].asText())
        assertEquals("Нет пропуска", rejection.second["comment"].asText())
        assertEquals("2026-10-13T09:00:00", rejection.second["createdAt"].asText())
        assertEquals("2026-10-14T10:00:01", rejection.second["updatedAt"].asText())
        assertEquals("Нет пропуска", jdbc.queryForObject("SELECT comment FROM pass_request WHERE id=?", String::class.java, rejected))
    }

    @Test fun `every transition from terminal statuses including repeats is rejected without mutation`() {
        for (source in listOf("APPROVED", "REJECTED", "CANCELLED")) {
            val id = seed(status = source)
            val before = snapshot()
            for (target in listOf("APPROVED", "REJECTED")) {
                val result = decision(id, """{"status":"$target","comment":"Причина"}""")
                assertEquals(409, result.first)
                assertEquals("Invalid status transition", result.second["error"].asText())
                assertEquals(before, snapshot())
            }
            assertEquals(409, request("POST", "/api/Ab12/requests/$id/cancel").first)
            assertEquals(before, snapshot())
        }
    }

    @Test fun `decision field errors precede transition errors and leave database intact`() {
        val id = seed(status = "APPROVED")
        val before = snapshot()
        for (status in listOf(null, "", "PENDING", "CANCELLED", "approved", "UNKNOWN")) {
            val result = decision(id, mapper.writeValueAsString(mapOf("status" to status)))
            assertEquals(400, result.first)
            assertEquals(setOf("status"), result.second["errors"].fieldNames().asSequence().toSet())
        }
        for (comment in listOf(null, "", "  ", "x".repeat(501))) {
            val result = decision(id, mapper.writeValueAsString(mapOf("status" to "REJECTED", "comment" to comment)))
            assertEquals(400, result.first)
            assertEquals(setOf("comment"), result.second["errors"].fieldNames().asSequence().toSet())
            assertTrue(result.second["errors"]["comment"].asText().isNotBlank())
        }
        assertEquals(before, snapshot())
        for (comment in listOf("x", "x".repeat(500))) {
            val pending = seed()
            assertEquals(200, decision(pending, mapper.writeValueAsString(mapOf("status" to "REJECTED", "comment" to comment))).first)
        }
    }

    @Test fun `access ownership and existence precede decision field validation`() {
        val id = seed(host = 2)
        val before = snapshot()
        for (key in listOf(null, "", "wrong")) {
            val result = decision(99999, "{}", key)
            assertEquals(403, result.first)
            assertEquals("Forbidden", result.second["error"].asText())
        }
        val missing = decision(99999, "{}")
        assertEquals(404, missing.first)
        assertEquals("Request not found", missing.second["error"].asText())
        for (path in listOf("/api/Ab12/requests/$id/cancel", "/api/Ab12/requests/99999/cancel")) {
            val result = request("POST", path)
            assertEquals(404, result.first)
            assertEquals("Request not found", result.second["error"].asText())
        }
        assertEquals(401, request("POST", "/api/ZZ99/requests/$id/cancel").first)
        assertEquals(before, snapshot())
    }

    @Test fun `approval followed by author cancellation sees current database state`() {
        val id = seed()
        assertEquals(200, decision(id, """{"status":"APPROVED"}""").first)
        val before = snapshot()
        assertEquals(409, request("POST", "/api/Ab12/requests/$id/cancel").first)
        assertEquals(before, snapshot())
        jdbc.update("UPDATE pass_request SET status='PENDING' WHERE id=?", id)
        assertEquals(200, request("POST", "/api/Ab12/requests/$id/cancel").first)
    }
}
