package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.user.UserEntity;

@SpringBootTest(properties = {
    "icastay.security.jwt.secret=12345678901234567890123456789012",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Transactional
class SuperadminHotelIntegrationTest {
    @Autowired HotelService hotels;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtTokenService tokens;
    @Autowired EntityManagerFactory entityManagerFactory;
    MockMvc mvc;
    String prefix;

    @BeforeEach void setup() {
        prefix = "Global-" + UUID.randomUUID();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test void stablePagesOfTwentyIncludeInactiveHotelsWithoutNPlusOne() throws Exception {
        List<SuperadminHotelView> expected = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            expected.add(hotel(prefix + (i == 0 ? " B" : " A"), i % 2 == 0 ? "ACTIVE" : "INACTIVE"));
        }
        expected.sort(Comparator.comparing(SuperadminHotelView::name).thenComparing(h -> h.id().toString()));
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var first = hotels.listGlobal(0, prefix, null);
        assertEquals(2, stats.getPrepareStatementCount(), "content and count only, no query per administrator");
        assertEquals(0, stats.getEntityLoadCount(), "DTO projection must not load JPA entities");
        assertEquals(expected.subList(0, 20), first.content());
        assertEquals(20, first.size());
        assertEquals(23, first.totalElements());
        assertEquals(2, first.totalPages());
        var second = hotels.listGlobal(1, prefix, null);
        assertEquals(expected.subList(20, 23), second.content());
        assertEquals(1, second.page());
        assertEquals(first, hotels.listGlobal(0, prefix, null));
        mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN"))
                .param("name", prefix).param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(23)).andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test void nameAndStatusFiltersCombineAndTreatWildcardsLiterally() {
        var active = hotel(prefix + " Valle", "ACTIVE");
        var inactive = hotel(prefix + " Valle", "INACTIVE");
        hotel(prefix + " Oasis", "ACTIVE");
        var literal = hotel(prefix + " %_", "INACTIVE");
        assertEquals(List.of(active), hotels.listGlobal(0, "  " + prefix.toUpperCase() + " vAlLe  ", "ACTIVE").content());
        assertEquals(List.of(inactive), hotels.listGlobal(0, prefix + " valle", "INACTIVE").content());
        assertEquals(2, hotels.listGlobal(0, prefix + " Valle", null).totalElements());
        assertEquals(List.of(literal), hotels.listGlobal(0, prefix + " %_", null).content());
        long inactiveCount = jdbc.queryForObject("SELECT count(*) FROM hotels WHERE status='INACTIVE'", Long.class);
        assertEquals(inactiveCount, hotels.listGlobal(0, null, "INACTIVE").totalElements());
        long all = jdbc.queryForObject("SELECT count(*) FROM hotels", Long.class);
        assertEquals(all, hotels.listGlobal(0, null, null).totalElements());
        assertEquals(all, hotels.listGlobal(0, "   ", null).totalElements());
    }

    @Test void dtoIncludesLocationAndAssignedAdminEvenWhenInactiveWithoutSensitiveFields() throws Exception {
        var row = hotel(prefix, "INACTIVE");
        jdbc.update("UPDATE users SET status='INACTIVE' WHERE id=?", row.adminUserId());
        mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN")).param("name", prefix))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].length()").value(8))
            .andExpect(jsonPath("$.content[0].id").value(row.id().toString()))
            .andExpect(jsonPath("$.content[0].name").value(prefix))
            .andExpect(jsonPath("$.content[0].address").value("Calle de prueba 123"))
            .andExpect(jsonPath("$.content[0].city").value("Ica"))
            .andExpect(jsonPath("$.content[0].status").value("INACTIVE"))
            .andExpect(jsonPath("$.content[0].adminUserId").value(row.adminUserId().toString()))
            .andExpect(jsonPath("$.content[0].adminName").value(row.adminName()))
            .andExpect(jsonPath("$.content[0].adminEmail").value(row.adminEmail()))
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.content[0].token").doesNotExist());
    }

    @Test void emptyAndOutOfRangePagesPreserveMetadata() throws Exception {
        hotel(prefix, "ACTIVE");
        var beyond = hotels.listGlobal(4, prefix, null);
        assertTrue(beyond.content().isEmpty());
        assertEquals(4, beyond.page()); assertEquals(1, beyond.totalElements()); assertEquals(1, beyond.totalPages());
        mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN"))
                .param("name", prefix).param("status", "INACTIVE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test void invalidPaginationAndFiltersReturn400() throws Exception {
        for (String page : new String[]{"-1", "107374183", "2147483648", "1.5", "bad"}) {
            mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN")).param("page", page))
                .andExpect(status().isBadRequest());
        }
        for (String state : new String[]{"", "active", "DISABLED", " ACTIVE "}) {
            mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN")).param("status", state))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN")).param("name", "x".repeat(181)))
            .andExpect(status().isBadRequest());
    }

    @Test void authenticationAndRoleBoundariesArePreserved() throws Exception {
        mvc.perform(get("/api/v1/superadmin/hotels")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER", "HOTEL_ADMIN"}) {
            mvc.perform(get("/api/v1/superadmin/hotels").header("Authorization", bearer(role))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/admin/hotel").header("Authorization", bearer("SUPER_ADMIN"))).andExpect(status().isForbidden());
        var inactive = hotel(prefix, "INACTIVE");
        mvc.perform(get("/api/v1/hotels").param("name", prefix)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/v1/hotels/" + inactive.id())).andExpect(status().isNotFound());
    }

    private SuperadminHotelView hotel(String name, String status) {
        UUID admin = UUID.randomUUID(), id = UUID.randomUUID();
        String adminName = "Admin " + admin, email = admin + "@example.test";
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,?,?,'unused','HOTEL_ADMIN')", admin, adminName, email);
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,address,city,status) VALUES (?,?,?,'Calle de prueba 123','Ica',?)", id, admin, name, status);
        return new SuperadminHotelView(id, name, "Calle de prueba 123", "Ica", status, admin, adminName, email);
    }

    private String bearer(String role) {
        var user = mock(UserEntity.class);
        var id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,'Auth test',?,'unused',?)", id, id + "@example.test", role);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role);
        return "Bearer " + tokens.createToken(user);
    }
}
