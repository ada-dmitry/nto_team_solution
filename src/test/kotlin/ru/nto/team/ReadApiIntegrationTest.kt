package ru.nto.team

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ReadApiIntegrationTest : IntegrationTestSupport() {
    @Test fun `profile checks exact case sensitive untrimmed ASCII code`() {
        assertEquals(200 to mapper.readTree("""{"name":"Первый Сотрудник"}"""), request("GET", "/api/Ab12/auth"))
        for (code in listOf("abc", "abcde", "Жб12", "Ab_2", " Ab1")) {
            val (status, json) = request("GET", "/api/$code/auth")
            assertEquals(400, status, code)
            assertEquals("Invalid code format", json["error"].asText())
        }
        for (code in listOf("ab12", "ZZ99")) {
            val (status, json) = request("GET", "/api/$code/auth")
            assertEquals(401, status)
            assertEquals("Unauthorized", json["error"].asText())
        }
    }

    @Test fun `list has exact fields ownership sorting filtering and empty array`() {
        val late = seed(from = "2026-10-14T12:00:00", to = "2026-10-14T13:00:00")
        val first = seed(status = "APPROVED")
        val second = seed()
        seed(host = 2)
        val (status, list) = request("GET", "/api/Ab12/requests")
        assertEquals(200, status)
        assertEquals(listOf(first, second, late), list.map { it["id"].asLong() })
        assertEquals(setOf("id", "fullName", "validFrom", "validTo", "status"), list[0].fieldNames().asSequence().toSet())
        assertEquals("2026-10-14T10:00:00", list[0]["validFrom"].asText())
        assertEquals(listOf(first), request("GET", "/api/Ab12/requests?status=APPROVED").second.map { it["id"].asLong() })
        for (s in RequestStatus.entries) assertEquals(200, request("GET", "/api/Ab12/requests?status=$s").first)
        assertEquals(mapper.readTree("[]"), request("GET", "/api/Ab12/requests?status=REJECTED").second)
        for (s in listOf("", "pending", "PENDING ", "UNKNOWN")) {
            val response = request("GET", "/api/Ab12/requests?status=$s")
            assertEquals(400, response.first)
            assertEquals("Invalid status", response.second["error"].asText())
        }
        assertEquals(401, request("GET", "/api/ZZ99/requests?status=bad").first)
    }

    @Test fun `details includes all fields nulls and hides foreign or missing requests`() {
        val id = seed()
        val (status, details) = request("GET", "/api/Ab12/requests/$id")
        assertEquals(200, status)
        assertEquals(setOf("id", "fullName", "phone", "organization", "purpose", "validFrom", "validTo", "status", "comment", "createdAt", "updatedAt"),
            details.fieldNames().asSequence().toSet())
        assertTrue(details["organization"].isNull)
        assertTrue(details["comment"].isNull)
        assertEquals("2026-10-13T09:00:00", details["createdAt"].asText())
        for (path in listOf("/api/Cd34/requests/$id", "/api/Ab12/requests/99999")) {
            val response = request("GET", path)
            assertEquals(404, response.first)
            assertEquals("Request not found", response.second["error"].asText())
        }
    }

    @Test fun `reads always see direct database edits without modifying data`() {
        val id = seed()
        val before = snapshot()
        request("GET", "/api/Ab12/requests/$id")
        request("GET", "/api/Ab12/requests")
        assertEquals(before, snapshot())
        jdbc.update("UPDATE pass_request SET status='CANCELLED', organization='Новая' WHERE id=?", id)
        assertEquals("CANCELLED", request("GET", "/api/Ab12/requests/$id").second["status"].asText())
        assertEquals(0, request("GET", "/api/Ab12/requests?status=PENDING").second.size())
        jdbc.update("UPDATE staff SET full_name='Другое Имя' WHERE id=1")
        assertEquals("Другое Имя", request("GET", "/api/Ab12/auth").second["name"].asText())
    }
}
