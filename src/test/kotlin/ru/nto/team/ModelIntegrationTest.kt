package ru.nto.team

import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime

class ModelIntegrationTest : IntegrationTestSupport() {
    @Autowired lateinit var emf: EntityManagerFactory
    @Autowired lateinit var txManager: PlatformTransactionManager

    @Test fun `JPA maps and commits generated identifiers association and string status`() {
        assertTrue(emf.metamodel.entities.any { it.javaType == Staff::class.java }, "Staff must be an entity")
        assertTrue(emf.metamodel.entities.any { it.javaType == PassRequest::class.java }, "PassRequest must be an entity")
        val id = TransactionTemplate(txManager).execute {
            val em = emf.createEntityManager()
            try {
                em.transaction.begin()
                val staff = Staff(code = "Xy90", fullName = "Новый Сотрудник")
                em.persist(staff)
                val from = LocalDateTime.parse("2026-10-14T12:00:00")
                val entity = PassRequest(host = staff, fullName = "Новый Посетитель", phone = "+79991234568",
                    organization = null, purpose = "Новая встреча", validFrom = from, validTo = from.plusHours(1),
                    status = RequestStatus.REJECTED, comment = "Отказ", createdAt = from, updatedAt = from)
                em.persist(entity)
                em.transaction.commit()
                entity.id
            } finally { em.close() }
        }!!
        assertTrue(id > 0)
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM pass_request WHERE id=?", String::class.java, id))
        val em = emf.createEntityManager()
        try { assertEquals("Xy90", em.find(PassRequest::class.java, id).host.code) } finally { em.close() }
    }
}
