package pe.edu.utp.icastay.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import pe.edu.utp.icastay.hotel.HotelService;
import pe.edu.utp.icastay.user.UserEntity;

@SpringBootTest(properties = "icastay.security.jwt.secret=12345678901234567890123456789012")
@Transactional
class UserReservationIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReservationRepository reservations;
    @Autowired ReservationUserRepository users;
    @Autowired ReservationRoomRepository rooms;
    @Autowired HotelService hotels;
    MockMvc mvc;
    UUID a, b, hotel, room, other;
    List<UUID> own;
    String bearer;
    final Instant now = Instant.parse("2030-01-01T12:00:00Z");
    ReservationService service;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        service = new ReservationService(reservations, users, rooms, hotels, Clock.fixed(now, ZoneOffset.UTC));
        a = user("USER"); b = user("USER"); UUID admin = user("HOTEL_ADMIN");
        hotel = UUID.randomUUID(); room = UUID.randomUUID();
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,address,city) VALUES (?,?,'Hotel de prueba','Calle 1','Ica')", hotel, admin);
        jdbc.update("INSERT INTO rooms(id,hotel_id,number,capacity,price_per_night) VALUES (?,?,'101',2,999)", room, hotel);
        own = new ArrayList<>();
        for (int i = 0; i < 21; i++) own.add(reservation(a));
        other = reservation(b);
        jdbc.update("UPDATE reservations SET created_at='2030-01-02T00:00:00Z' WHERE id=?", own.get(0));
        bearer = "Bearer " + token(a, "USER");
    }
    UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,'Test',?,'unused',?)", id, id + "@example.test", role);
        return id;
    }
    UUID reservation(UUID guest) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO reservations(id,guest_user_id,room_id,check_in,check_out,guests,status,expires_at,
                nightly_price_snapshot,total_amount,currency,created_at)
            VALUES (?,?,?,'2030-01-10','2030-01-12',2,'PENDING_PAYMENT','2030-01-01T12:00:00Z',125.50,251,'PEN','2030-01-01T00:00:00Z')
            """, id, guest, room);
        return id;
    }
    String token(UUID id, String role) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id); when(user.getEmail()).thenReturn(id + "@example.test"); when(user.getRole()).thenReturn(role);
        return tokens.createToken(user);
    }
    @Test void ownershipAndPaginationAreEnforcedInQueries() throws Exception {
        mvc.perform(get("/api/v1/reservations/me").header("Authorization", bearer).param("guestUserId", b.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(20)).andExpect(jsonPath("$.content[0].id").value(own.get(0).toString()));
        mvc.perform(get("/api/v1/reservations/me").header("Authorization", "Bearer " + token(b, "USER")))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(other.toString()));
        var expected = new ArrayList<String>(); expected.add(own.get(0).toString());
        expected.addAll(own.subList(1, own.size()).stream().map(UUID::toString).sorted(Comparator.reverseOrder()).toList());
        var actual = new ArrayList<String>();
        actual.addAll(service.listOwn(a, 0).content().stream().map(item -> item.id().toString()).toList());
        actual.addAll(service.listOwn(a, 1).content().stream().map(item -> item.id().toString()).toList());
        assertEquals(expected, actual);
        assertTrue(service.listOwn(a, 2).content().isEmpty());
        assertEquals(21, service.listOwn(a, 2).totalElements());
        for (UUID id : List.of(other, UUID.randomUUID())) {
            mvc.perform(get("/api/v1/reservations/" + id).header("Authorization", bearer).param("guestUserId", b.toString()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/reservations/" + own.get(0)).header("Authorization", "Bearer " + token(b, "USER")))
                .andExpect(status().isNotFound());
    }
    @Test void inactiveResourcesKeepHistoryAndOriginalAmounts() throws Exception {
        jdbc.update("UPDATE hotels SET status='INACTIVE' WHERE id=?", hotel);
        jdbc.update("UPDATE rooms SET status='INACTIVE', price_per_night=9999, currency='USD' WHERE id=?", room);
        mvc.perform(get("/api/v1/reservations/" + own.get(0)).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hotelName").value("Hotel de prueba"))
                .andExpect(jsonPath("$.roomNumber").value("101")).andExpect(jsonPath("$.nightlyPriceSnapshot").value(125.50))
                .andExpect(jsonPath("$.totalAmount").value(251)).andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.guestUserId").doesNotExist()).andExpect(jsonPath("$.guestEmail").doesNotExist());
        assertEquals(21, service.listOwn(a, 0).totalElements());
    }
    @Test void expiryUsesServerClockExactlyWithoutUpdatingRows() {
        UUID id = own.get(0);
        for (long offset : new long[] {-1, 0, 1}) {
            jdbc.update("UPDATE reservations SET expires_at=? WHERE id=?", java.sql.Timestamp.from(now.plusSeconds(offset)), id);
            var before = jdbc.queryForMap("SELECT * FROM reservations WHERE id=?", id);
            var detail = service.findOwn(a, id);
            assertEquals(offset <= 0, detail.holdExpired()); assertEquals(now, detail.evaluatedAt());
            assertEquals("PENDING_PAYMENT", detail.status());
            assertEquals(detail, service.listOwn(a, 0).content().get(0));
            assertEquals(before, jdbc.queryForMap("SELECT * FROM reservations WHERE id=?", id));
        }
        for (String state : List.of("CONFIRMED", "CANCELLED", "EXPIRED")) {
            jdbc.update("UPDATE reservations SET status=?, expires_at=null WHERE id=?", state, id);
            assertEquals(state, service.findOwn(a, id).status()); assertFalse(service.findOwn(a, id).holdExpired());
        }
    }
    @Test void securityAndInvalidPagination() throws Exception {
        for (String path : List.of("/api/v1/reservations/me", "/api/v1/reservations/" + own.get(0))) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", "Bearer invalid.jwt")).andExpect(status().isUnauthorized());
            for (String role : List.of("HOTEL_ADMIN", "SUPER_ADMIN")) {
                mvc.perform(get(path).header("Authorization", "Bearer " + token(a, role))).andExpect(status().isForbidden());
            }
        }
        for (String page : List.of("-1", "2147483647", "bad")) {
            mvc.perform(get("/api/v1/reservations/me").header("Authorization", bearer).param("page", page)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/reservations/invalid").header("Authorization", bearer)).andExpect(status().isBadRequest());
        assertEquals(0, service.listOwn(user("USER"), 0).totalElements());
    }
}
