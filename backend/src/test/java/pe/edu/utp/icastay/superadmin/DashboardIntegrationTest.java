package pe.edu.utp.icastay.superadmin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
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

@SpringBootTest(properties = "icastay.security.jwt.secret=12345678901234567890123456789012")
@Transactional
class DashboardIntegrationTest {
    @Autowired DashboardRepository repository;
    @Autowired DashboardService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtTokenService tokens;
    MockMvc mvc;
    final Instant now = Instant.parse("2030-01-01T12:00:00Z");

    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build(); }

    @Test void aggregatesAllRecordsAndSplitsExpiryWithoutWrites() {
        var fixed = new DashboardService(repository, Clock.fixed(now, ZoneOffset.UTC));
        var before = fixed.get();
        UUID admin = user("HOTEL_ADMIN", "ACTIVE");
        UUID guest = user("USER", "INACTIVE");
        user("SUPER_ADMIN", "ACTIVE");
        UUID hotel = UUID.randomUUID(), room = UUID.randomUUID();
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,address,city,status) VALUES (?,?,'Dashboard test','Test','Ica','INACTIVE')", hotel, admin);
        jdbc.update("INSERT INTO rooms(id,hotel_id,number,capacity,price_per_night,status) VALUES (?,?,'101',2,100,'INACTIVE')", room, hotel);
        UUID current = reservation(guest, room, "PENDING_PAYMENT", now.plusSeconds(1));
        UUID exact = reservation(guest, room, "PENDING_PAYMENT", now);
        UUID overdue = reservation(guest, room, "PENDING_PAYMENT", now.minusSeconds(1));
        reservation(guest, room, "CONFIRMED", null);
        reservation(guest, room, "CANCELLED", null);
        reservation(guest, room, "EXPIRED", null);
        for (String result : new String[]{"APPROVED", "REJECTED", "REJECTED"}) {
            jdbc.update("INSERT INTO payment_attempts(id,reservation_id,status) VALUES (?,?,?)", UUID.randomUUID(), current, result);
        }
        var after = fixed.get();
        assertEquals(before.hotels() + 1, after.hotels());
        assertEquals(before.rooms() + 1, after.rooms());
        assertEquals(before.users() + 3, after.users());
        var b = before.reservations(); var a = after.reservations();
        assertEquals(b.total() + 6, a.total());
        assertEquals(b.pendingPayment() + 3, a.pendingPayment());
        assertEquals(b.pendingCurrent() + 1, a.pendingCurrent());
        assertEquals(b.pendingExpired() + 2, a.pendingExpired());
        assertEquals(b.confirmed() + 1, a.confirmed());
        assertEquals(b.cancelled() + 1, a.cancelled());
        assertEquals(b.expired() + 1, a.expired());
        assertEquals(a.pendingPayment(), a.pendingCurrent() + a.pendingExpired());
        assertEquals(before.paymentAttempts().get("APPROVED") + 1, after.paymentAttempts().get("APPROVED"));
        assertEquals(before.paymentAttempts().get("REJECTED") + 2, after.paymentAttempts().get("REJECTED"));
        assertEquals(now, after.generatedAt());
        assertEquals(after, fixed.get());
        for (UUID id : new UUID[]{current, exact, overdue}) {
            assertEquals("PENDING_PAYMENT", jdbc.queryForObject("SELECT status FROM reservations WHERE id=?", String.class, id));
        }
    }

    @Test void superAdminReceivesGlobalDto() throws Exception {
        var authorization = bearer("SUPER_ADMIN");
        var expected = service.get();
        mvc.perform(get("/api/v1/superadmin/dashboard").header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hotels").value(expected.hotels()))
            .andExpect(jsonPath("$.rooms").value(expected.rooms()))
            .andExpect(jsonPath("$.users").value(expected.users()))
            .andExpect(jsonPath("$.reservations.total").value(expected.reservations().total()))
            .andExpect(jsonPath("$.paymentAttempts.APPROVED").isNumber())
            .andExpect(jsonPath("$.paymentAttempts.REJECTED").isNumber())
            .andExpect(jsonPath("$.generatedAt").isNotEmpty())
            .andExpect(jsonPath("$.income").doesNotExist());
    }

    @Test void missingOrInvalidTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/superadmin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/superadmin/dashboard").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
    }

    @Test void otherRolesAreForbiddenAndHotelAdminBoundaryIsUnchanged() throws Exception {
        for (String role : new String[]{"USER", "HOTEL_ADMIN"}) {
            mvc.perform(get("/api/v1/superadmin/dashboard").header("Authorization", bearer(role)))
                .andExpect(status().isForbidden());
        }
        for (String path : new String[]{"/api/v1/admin/hotel", "/api/v1/admin/hotel/rooms", "/api/v1/admin/hotel/reservations", "/api/v1/reservations/me"}) {
            mvc.perform(get(path).header("Authorization", bearer("SUPER_ADMIN"))).andExpect(status().isForbidden());
        }
    }

    private UUID user(String role, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role,status) VALUES (?,'Dashboard test',?,'unused',?,?)", id, id + "@example.test", role, status);
        return id;
    }

    private UUID reservation(UUID guest, UUID room, String status, Instant expiry) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reservations(id,guest_user_id,room_id,check_in,check_out,guests,status,expires_at,nightly_price_snapshot,total_amount,currency)
            VALUES (?,?,?,'2030-02-01','2030-02-02',1,?,?,100,100,'PEN')
            """, id, guest, room, status, expiry == null ? null : Timestamp.from(expiry));
        return id;
    }

    private String bearer(String role) {
        UserEntity user = mock(UserEntity.class);
        var id = user(role, "ACTIVE");
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role);
        return "Bearer " + tokens.createToken(user);
    }
}
