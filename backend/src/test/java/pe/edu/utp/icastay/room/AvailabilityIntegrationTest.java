package pe.edu.utp.icastay.room;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import pe.edu.utp.icastay.hotel.HotelService;

@SpringBootTest(properties = "icastay.security.jwt.secret=12345678901234567890123456789012")
@Transactional
class AvailabilityIntegrationTest {
    @Autowired RoomRepository rooms;
    @Autowired HotelService hotels;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    RoomService service;
    UUID hotel, room, user;
    MockMvc mvc;
    final Instant now = Instant.parse("2030-01-01T12:00:00Z");
    @BeforeEach void setup() {
        service = new RoomService(rooms, hotels, Clock.fixed(now, ZoneOffset.UTC));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        user = UUID.randomUUID(); hotel = UUID.randomUUID(); room = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,?,?,?,?)", user, "Test", user + "@example.test", "unused", "HOTEL_ADMIN");
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,address,city) VALUES (?,?,?,?,?)", hotel, user, "Test", "Test", "Ica");
        jdbc.update("INSERT INTO rooms(id,hotel_id,number,capacity,price_per_night) VALUES (?,?,?,?,?)", room, hotel, "101", 2, 100);
    }
    void reservation(String state, String start, String end, Instant expiry) {
        jdbc.update("INSERT INTO reservations(id,guest_user_id,room_id,check_in,check_out,guests,status,expires_at,nightly_price_snapshot,total_amount,currency) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), user, room, LocalDate.parse(start), LocalDate.parse(end), 1, state,
                expiry == null ? null : java.sql.Timestamp.from(expiry), 100, 200, "PEN");
    }
    int available() { return service.available(hotel, "2030-01-02", "2030-01-04", 2).orElseThrow().size(); }
    @Test void capacityAndInactiveResources() {
        assertEquals(1, available());
        assertEquals(0, service.available(hotel, "2030-01-02", "2030-01-04", 3).orElseThrow().size());
        jdbc.update("UPDATE rooms SET status='INACTIVE' WHERE id=?", room); assertEquals(0, available());
        jdbc.update("UPDATE hotels SET status='INACTIVE' WHERE id=?", hotel);
        assertTrue(service.available(hotel, "2030-01-02", "2030-01-04", 2).isEmpty());
        assertTrue(service.available(UUID.randomUUID(), "2030-01-02", "2030-01-04", 2).isEmpty());
    }
    @Test void confirmedOverlapsButAdjacentStaysDoNot() {
        reservation("CONFIRMED", "2030-01-01", "2030-01-02", null);
        reservation("CONFIRMED", "2030-01-04", "2030-01-05", null);
        assertEquals(1, available());
        reservation("CONFIRMED", "2030-01-03", "2030-01-05", null); assertEquals(0, available());
    }
    @Test void encompassingAndContainedOverlaps() {
        reservation("CONFIRMED", "2030-01-01", "2030-01-05", null); assertEquals(0, available());
        jdbc.update("DELETE FROM reservations WHERE room_id=?", room);
        reservation("CONFIRMED", "2030-01-02", "2030-01-03", null); assertEquals(0, available());
    }
    @Test void exactExpiryAndNonBlockingStates() {
        reservation("CANCELLED", "2030-01-02", "2030-01-04", null);
        reservation("EXPIRED", "2030-01-02", "2030-01-04", null);
        reservation("PENDING_PAYMENT", "2030-01-02", "2030-01-04", now.minusSeconds(1));
        reservation("PENDING_PAYMENT", "2030-01-02", "2030-01-04", now);
        assertEquals(1, available());
        reservation("PENDING_PAYMENT", "2030-01-02", "2030-01-04", now.plusSeconds(1)); assertEquals(0, available());
        assertEquals(1, service.available(hotel, null, null, null).orElseThrow().size());
    }
    @Test void validatesDatesAgainstFixedClock() {
        for (String[] dates : new String[][] {{"2029-12-31", "2030-01-03"}, {"2030-01-02", "2030-01-02"},
                {"2030-01-03", "2030-01-02"}, {"2030-02-30", "2030-03-01"}, {"", "2030-01-04"}, {null, "2030-01-04"}}) {
            var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> service.available(hotel, dates[0], dates[1], 1));
            assertEquals(400, error.getStatusCode().value());
        }
        assertEquals(1, service.available(hotel, "2030-01-01", "2030-01-02", 1).orElseThrow().size());
    }
    @Test void publicHttpValidationAndCompatibility() throws Exception {
        String path = "/api/v1/hotels/" + hotel + "/rooms";
        mvc.perform(get(path)).andExpect(status().isOk());
        String future = LocalDate.now(ZoneOffset.UTC).plusDays(2).toString();
        String later = LocalDate.now(ZoneOffset.UTC).plusDays(3).toString();
        mvc.perform(get(path).param("checkIn", future).param("checkOut", later).param("guests", "2")).andExpect(status().isOk());
        mvc.perform(get(path).param("checkIn", future)).andExpect(status().isBadRequest());
        for (String guests : new String[] {"", "0", "-1", "1.5", "2147483648"}) {
            mvc.perform(get(path).param("checkIn", future).param("checkOut", later).param("guests", guests)).andExpect(status().isBadRequest());
        }
    }
}
