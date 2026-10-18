package ru.nto.team

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ContractIntegrationTest : IntegrationTestSupport() {
    @Test fun `full lifecycle returns consistent UTF8 details and list projections`() {
        val (createdStatus, created) = request("POST", "/api/Ab12/requests", body("organization" to "ООО НТО"))
        assertEquals(201, createdStatus)
        val id = created["id"].asLong()
        val rejected = request("PATCH", "/api/admin/requests/$id", """{"status":"REJECTED","comment":"Не согласовано"}""", "test-admin")
        assertEquals(200, rejected.first)
        assertEquals("Первый Гость", rejected.second["fullName"].asText())
        assertEquals("ООО НТО", rejected.second["organization"].asText())
        assertEquals("Не согласовано", rejected.second["comment"].asText())
        assertEquals(rejected.second, request("GET", "/api/Ab12/requests/$id").second)
        val list = request("GET", "/api/Ab12/requests?status=REJECTED")
        assertEquals(200, list.first)
        assertEquals(1, list.second.size())
        for (field in list.second[0].fieldNames()) assertEquals(rejected.second[field], list.second[0][field])
        assertEquals("Request not found", request("GET", "/api/Cd34/requests/$id").second["error"].asText())
    }

    @Test fun `unknown codes are unauthorized on every user endpoint without mutation`() {
        val id = seed()
        val before = snapshot()
        for ((method, path, content) in listOf(
            Triple("GET", "/api/ZZ99/auth", null), Triple("GET", "/api/ZZ99/requests?status=bad", null),
            Triple("GET", "/api/ZZ99/requests/$id", null), Triple("POST", "/api/ZZ99/requests", "{}"),
            Triple("POST", "/api/ZZ99/requests/$id/cancel", null))) {
            val result = request(method, path, content)
            assertEquals(401, result.first)
            assertEquals(mapper.readTree("""{"error":"Unauthorized"}"""), result.second)
        }
        assertEquals(before, snapshot())
    }

    @Test fun `malformed JSON and nonstring fields return protected error without writes`() {
        val id = seed()
        val before = snapshot()
        for (content in listOf("{", "null", "[]", """{"phone":123}""", """{"purpose":true}""", """{"validFrom":{}}""")) {
            val result = request("POST", "/api/Ab12/requests", content)
            assertEquals(400, result.first, content)
            assertEquals(mapper.readTree("""{"error":"Invalid request body"}"""), result.second)
        }
        val result = request("PATCH", "/api/admin/requests/$id", """{"status":false}""", "test-admin")
        assertEquals(400, result.first)
        assertEquals("Invalid request body", result.second["error"].asText())
        assertEquals(before, snapshot())
    }

    @Test fun `access and existence precede malformed body as specified in section 6_3`() {
        val before = snapshot()
        val cases = listOf(
            Triple("/api/ZZ99/requests", 401, "Unauthorized"),
            Triple("/api/bad/requests", 400, "Invalid code format"),
        )
        for ((path, expectedStatus, error) in cases) {
            val response = request("POST", path, "{")
            assertEquals(expectedStatus, response.first, path)
            assertEquals(error, response.second["error"].asText(), path)
        }
        val missing = request("PATCH", "/api/admin/requests/99999", "{", "test-admin")
        assertEquals(404, missing.first)
        assertEquals("Request not found", missing.second["error"].asText())
        assertEquals(before, snapshot())
    }

    @Test fun `missing admin request precedes malformed or nonstring or empty body`() {
        for (body in listOf("{", "", "null", """{"status":123}""")) {
            val result = request("PATCH", "/api/admin/requests/99999", body, "test-admin")
            assertEquals(404, result.first, body)
            assertEquals("Request not found", result.second["error"].asText())
        }
        assertEquals(0L, count())
    }

    @Test fun `code validation precedes empty and nonstring bodies but admin key stays first`() {
        for (body in listOf("", "null", """{"phone":123}""")) {
            val unknown = request("POST", "/api/ZZ99/requests", body)
            assertEquals(401, unknown.first, body)
            val invalid = request("POST", "/api/bad/requests", body)
            assertEquals(400, invalid.first, body)
            assertEquals("Invalid code format", invalid.second["error"].asText())
        }
        for (body in listOf("{", "", "null", """{"status":123}""")) {
            val denied = request("PATCH", "/api/admin/requests/99999", body, "wrong")
            assertEquals(403, denied.first)
            assertEquals("Forbidden", denied.second["error"].asText())
        }
        assertEquals(0L, count())
    }
}
