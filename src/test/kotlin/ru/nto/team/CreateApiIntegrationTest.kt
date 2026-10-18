package ru.nto.team

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class CreateApiIntegrationTest : IntegrationTestSupport() {
    @Test fun `creation commits author initial status nulls timestamps and full response`() {
        clock.current = Instant.parse("2026-10-14T06:00:00.789Z")
        val (status, result) = request("POST", "/api/Ab12/requests", body("status" to "APPROVED", "hostId" to 2,
            "comment" to "ignored", "createdAt" to "2000-01-01T00:00:00"))
        assertEquals(201, status)
        val id = result["id"].asLong()
        assertTrue(id > 0)
        assertEquals(1L, count())
        assertEquals(1L, jdbc.queryForObject("SELECT host_id FROM pass_request WHERE id=?", Long::class.java, id))
        assertEquals("PENDING", result["status"].asText())
        assertTrue(result["organization"].isNull)
        assertTrue(result["comment"].isNull)
        assertEquals("2026-10-14T09:00:00", result["createdAt"].asText())
        assertEquals(result["createdAt"], result["updatedAt"])
        assertEquals(result, request("GET", "/api/Ab12/requests/$id").second)
        assertEquals("2026-10-14T09:00", jdbc.queryForObject("SELECT created_at FROM pass_request WHERE id=?", java.time.LocalDateTime::class.java, id).toString())
    }

    @Test fun `absent null empty or whitespace organization becomes null`() {
        for (organization in listOf(null, "", "  ")) {
            jdbc.execute("DELETE FROM pass_request")
            val (status, result) = request("POST", "/api/Ab12/requests", body("organization" to organization))
            assertEquals(201, status)
            assertTrue(result["organization"].isNull)
        }
    }

    @Test fun `duplicate overlap is half open and includes containing contained and equal intervals`() {
        seed()
        val before = snapshot()
        for ((from, to) in listOf("09:30:00" to "10:30:00", "10:30:00" to "11:30:00",
            "10:10:00" to "10:20:00", "09:30:00" to "11:30:00", "10:00:00" to "11:00:00")) {
            val response = request("POST", "/api/Ab12/requests", body("validFrom" to "2026-10-14T$from", "validTo" to "2026-10-14T$to"))
            assertEquals(409, response.first)
            assertEquals("Duplicate request", response.second["error"].asText())
            assertEquals(before, snapshot())
        }
        assertEquals(201, request("POST", "/api/Ab12/requests", body("validFrom" to "2026-10-14T09:00:00", "validTo" to "2026-10-14T10:00:00")).first)
        assertEquals(201, request("POST", "/api/Ab12/requests", body("validFrom" to "2026-10-14T11:00:00", "validTo" to "2026-10-14T12:00:00")).first)
    }

    @Test fun `only same author phone and active statuses block creation`() {
        for (status in RequestStatus.entries) {
            jdbc.execute("DELETE FROM pass_request")
            seed(status = status.name)
            assertEquals(if (status in listOf(RequestStatus.PENDING, RequestStatus.APPROVED)) 409 else 201,
                request("POST", "/api/Ab12/requests", body("phone" to " +79991234567 ")).first)
        }
        jdbc.execute("DELETE FROM pass_request")
        seed(host = 2)
        assertEquals(201, request("POST", "/api/Ab12/requests", body()).first)
        assertEquals(201, request("POST", "/api/Ab12/requests", body("phone" to "+79991234568")).first)
    }

    @Test fun `access precedes validation and validation precedes duplicate without mutations`() {
        seed()
        val before = snapshot()
        assertEquals(401, request("POST", "/api/ZZ99/requests", "{}").first)
        assertEquals(400, request("POST", "/api/Ab12/requests", body("fullName" to "")).first)
        assertEquals(before, snapshot())
    }
}
