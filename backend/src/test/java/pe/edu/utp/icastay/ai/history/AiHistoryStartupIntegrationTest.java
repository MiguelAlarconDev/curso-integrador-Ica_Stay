package pe.edu.utp.icastay.ai.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Full core startup on an explicitly isolated PostgreSQL, with Mongo deliberately unavailable. */
@EnabledIfEnvironmentVariable(named = "ICASTAY_TEST_POSTGRES", matches = "isolated")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/icastay_ai_infra_test",
        "spring.datasource.username=postgres", "spring.datasource.password=",
        "spring.mongodb.uri=mongodb://127.0.0.1:27029/icastay_ai_test_unavailable",
        "logging.level.org.mongodb.driver=OFF", "debug=false"
})
class AiHistoryStartupIntegrationTest {
    @DynamicPropertySource
    static void ephemeralTestConfiguration(DynamicPropertyRegistry properties) {
        String testKey = java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID();
        properties.add("icastay.security.jwt.secret", () -> testKey);
    }

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired EntityManagerFactory jpa;
    @Autowired Flyway flyway;
    @Autowired MongoTemplate mongo;
    @Autowired AiHistoryService history;

    @Test
    void coreStartsAndPublicCatalogRemainsHealthyWithMongoDown() throws Exception {
        assertTrue(jpa.isOpen());
        assertEquals("2", flyway.info().current().getVersion().getVersion());
        assertNotNull(mongo);
        assertFalse(context.containsBean("mongoHealthContributor"));
        assertFalse(context.containsBean("mongoTransactionManager"));
        var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/v1/hotels")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(post("/api/v1/reservations")).andExpect(status().isUnauthorized());
        assertEquals(AiHistoryService.StoreResult.UNAVAILABLE, history.storeCompleted(java.util.UUID.randomUUID().toString(),
                AiHistoryServiceTest.token(7), "Q", "A", "model"));
        mvc.perform(get("/api/v1/hotels")).andExpect(status().isOk());
    }
}
