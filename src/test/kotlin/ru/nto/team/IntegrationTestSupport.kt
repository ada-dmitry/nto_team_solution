package ru.nto.team

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import java.time.*

class TestClock : Clock() {
    var current: Instant = Instant.parse("2026-10-14T06:00:00Z")
    var zoneId: ZoneId = ZoneId.of("Europe/Moscow")
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    override fun instant(): Instant = current
}

@SpringBootTest(properties = ["ADMIN_KEY=test-admin"])
@AutoConfigureMockMvc
@Import(IntegrationTestSupport.ClockConfig::class)
abstract class IntegrationTestSupport {
    @TestConfiguration
    class ClockConfig {
        @Bean @Primary fun testClock() = TestClock()
    }

    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var mapper: ObjectMapper
    @Autowired lateinit var clock: TestClock

    @BeforeEach
    fun reset() {
        val url = jdbc.dataSource!!.connection.use { it.metaData.url }
        check(url.substringAfterLast('/').startsWith("nto_team_solution_test")) {
            "Tests require a dedicated nto_team_solution_test database"
        }
        jdbc.execute("TRUNCATE pass_request, staff RESTART IDENTITY CASCADE")
        jdbc.update("INSERT INTO staff(code,full_name) VALUES ('Ab12','Первый Сотрудник'),('Cd34','Второй Сотрудник')")
        clock.current = Instant.parse("2026-10-14T06:00:00Z")
        clock.zoneId = ZoneId.of("Europe/Moscow")
    }

    fun seed(host: Long = 1, status: String = "PENDING", from: String = "2026-10-14T10:00:00",
             to: String = "2026-10-14T11:00:00", phone: String = "+79991234567"): Long =
        jdbc.queryForObject("""INSERT INTO pass_request(host_id,full_name,phone,purpose,valid_from,valid_to,status,created_at,updated_at)
            VALUES (?, 'Первый Гость', ?, 'Рабочая встреча', ?::timestamp, ?::timestamp, ?,
            '2026-10-13 09:00:00', '2026-10-13 09:00:00') RETURNING id""", Long::class.java, host, phone, from, to, status)!!

    fun count(): Long = jdbc.queryForObject("SELECT count(*) FROM pass_request", Long::class.java)!!
    fun snapshot(): List<Map<String, Any>> = jdbc.queryForList("SELECT * FROM pass_request ORDER BY id")
    fun request(method: String, path: String, body: String? = null, key: String? = null): Pair<Int, JsonNode> {
        val builder = when (method) {
            "GET" -> get(path)
            "POST" -> post(path)
            "PATCH" -> patch(path)
            else -> error("Unsupported method")
        }
        if (body != null) builder.contentType("application/json").content(body)
        if (key != null) builder.header("X-Admin-Key", key)
        val result = mvc.perform(builder).andReturn().response
        return result.status to mapper.readTree(result.contentAsByteArray)
    }

    fun body(vararg overrides: Pair<String, Any?>): String {
        val values = linkedMapOf<String, Any?>("fullName" to "Первый Гость", "phone" to "+79991234567",
            "purpose" to "Рабочая встреча", "validFrom" to "2026-10-14T10:00:00", "validTo" to "2026-10-14T11:00:00")
        values.putAll(overrides)
        return mapper.writeValueAsString(values)
    }
}
