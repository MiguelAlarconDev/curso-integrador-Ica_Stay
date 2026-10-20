package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.reservation.CreateReservationRequest;
import pe.edu.utp.icastay.reservation.ReservationService;
import pe.edu.utp.icastay.user.UserEntity;

@SpringBootTest(properties = "icastay.security.jwt.secret=12345678901234567890123456789012")
class EditHotelIntegrationTest {
    @Autowired HotelService hotels;
    @Autowired ReservationService reservations;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtTokenService tokens;
    MockMvc mvc;
    final java.util.List<UUID> actors = new java.util.ArrayList<>();
    UUID hotel, admin, guest, room;
    String path;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        hotel = UUID.randomUUID(); admin = user("HOTEL_ADMIN"); guest = user("USER"); room = UUID.randomUUID();
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,description,address,city) VALUES (?,?,'Original','Descripción','Calle 1','Ica')", hotel, admin);
        jdbc.update("INSERT INTO rooms(id,hotel_id,number,capacity,price_per_night) VALUES (?,?,'101',2,100)", room, hotel);
        path = "/api/v1/superadmin/hotels/" + hotel;
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM reservations WHERE room_id=?", room);
        jdbc.update("DELETE FROM rooms WHERE id=?", room);
        jdbc.update("DELETE FROM hotels WHERE id=?", hotel);
        jdbc.update("DELETE FROM users WHERE id IN (?,?)", admin, guest);
        for (UUID id : actors) jdbc.update("DELETE FROM users WHERE id=?", id);
        actors.clear();
    }
    UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,'Test admin',?,'unused',?)", id, id + "@example.test", role);
        return id;
    }
    String bearer(String role) {
        var id = user(role); actors.add(id);
        var user = mock(UserEntity.class); when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(id + "@example.test"); when(user.getRole()).thenReturn(role);
        return "Bearer " + tokens.createToken(user);
    }
    CreateReservationRequest booking() {
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(60);
        return new CreateReservationRequest(room, date, date.plusDays(2), 1);
    }
    @Test void detailAndEditIncludeDescriptionAndPreserveOwnerAndStatus() throws Exception {
        hotels.changeGlobalStatus(hotel, new HotelStatusRequest("INACTIVE"));
        mvc.perform(get(path).header("Authorization", bearer("SUPER_ADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(9)).andExpect(jsonPath("$.description").value("Descripción"))
            .andExpect(jsonPath("$.adminUserId").value(admin.toString())).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(put(path).header("Authorization", bearer("SUPER_ADMIN")).contentType("application/json").content("""
            {"name":" Nuevo ","description":" Nueva descripción ","address":" Calle 2 ","city":" Paracas ",
             "adminUserId":"%s","status":"ACTIVE","roomId":"%s","reservations":[]}
            """.formatted(guest, room)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nuevo"))
            .andExpect(jsonPath("$.description").value("Nueva descripción"))
            .andExpect(jsonPath("$.address").value("Calle 2")).andExpect(jsonPath("$.city").value("Paracas"))
            .andExpect(jsonPath("$.status").value("INACTIVE")).andExpect(jsonPath("$.adminUserId").value(admin.toString()));
        assertEquals("Nuevo", hotels.findGlobal(hotel).name());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM users WHERE id=?", String.class, admin));
    }

    @Test void listedHotelIdResolvesAdministrativeDetailForBothStatesWhilePublicDetailStaysFiltered() throws Exception {
        String name = "detail-regression-" + hotel;
        hotels.updateGlobal(hotel, new UpdateHotelRequest(name, "Description", "Address", "Ica"));
        for (String state : new String[]{"ACTIVE", "INACTIVE"}) {
            hotels.changeGlobalStatus(hotel, new HotelStatusRequest(state));
            mvc.perform(get("/api/v1/superadmin/hotels").param("name", name).param("status", state)
                    .header("Authorization", bearer("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(hotel.toString()))
                .andExpect(jsonPath("$.content[0].adminUserId").value(admin.toString()));
            mvc.perform(get(path).header("Authorization", bearer("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(hotel.toString()))
                .andExpect(jsonPath("$.status").value(state)).andExpect(jsonPath("$.description").value("Description"));
            mvc.perform(get("/api/v1/hotels/" + hotel)).andExpect(status().is(state.equals("ACTIVE") ? 200 : 404));
        }
    }
    @Test void validatesFieldsAndStateWithoutPartialUpdates() throws Exception {
        var original = hotels.findGlobal(hotel);
        for (String body : new String[]{"{}", "null", "{", "{\"name\":\" \"}",
                "{\"name\":\"" + "x".repeat(181) + "\",\"address\":\"Calle\",\"city\":\"Ica\"}"}) {
            mvc.perform(put(path).header("Authorization", bearer("SUPER_ADMIN")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        for (var request : new UpdateHotelRequest[]{new UpdateHotelRequest("Name", "x".repeat(5001), "Street", "Ica"),
                new UpdateHotelRequest("Name", null, "x".repeat(256), "Ica"), new UpdateHotelRequest("Name", null, "Street", "x".repeat(121))}) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> hotels.updateGlobal(hotel, request)).getStatusCode().value());
        }
        for (String body : new String[]{"{}", "null", "{", "{\"status\":\"active\"}", "{\"status\":\"DISABLED\"}", "{\"status\":null}"}) {
            mvc.perform(patch(path + "/status").header("Authorization", bearer("SUPER_ADMIN")).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(original, hotels.findGlobal(hotel));
        var limits = hotels.updateGlobal(hotel, new UpdateHotelRequest("x".repeat(180), "x".repeat(5000), "x".repeat(255), "x".repeat(120)));
        assertEquals(5000, limits.description().length());
        assertNull(hotels.updateGlobal(hotel, new UpdateHotelRequest("Name", "  ", "Street", "Ica")).description());
    }
    @Test void deactivationPreservesRoomsAndAllReservationStatesAndBlocksNewBookings() throws Exception {
        for (String state : new String[]{"PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED"}) {
            jdbc.update("""
                INSERT INTO reservations(id,guest_user_id,room_id,check_in,check_out,guests,status,expires_at,nightly_price_snapshot,total_amount,currency)
                VALUES (?,?,?,CURRENT_DATE+1,CURRENT_DATE+2,1,?,CURRENT_TIMESTAMP+INTERVAL '15 minutes',100,100,'PEN')
                """, UUID.randomUUID(), guest, room, state);
        }
        var before = jdbc.queryForList("SELECT * FROM reservations WHERE room_id=? ORDER BY id", room);
        var beforeRoom = jdbc.queryForMap("SELECT * FROM rooms WHERE id=?", room);
        for (int i = 0; i < 2; i++) mvc.perform(patch(path + "/status").header("Authorization", bearer("SUPER_ADMIN"))
                .contentType("application/json").content("{\"status\":\"INACTIVE\",\"adminUserId\":\"" + guest + "\",\"name\":\"ignored\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"))
            .andExpect(jsonPath("$.name").value("Original")).andExpect(jsonPath("$.adminUserId").value(admin.toString()));
        assertEquals(before, jdbc.queryForList("SELECT * FROM reservations WHERE room_id=? ORDER BY id", room));
        assertEquals(beforeRoom, jdbc.queryForMap("SELECT * FROM rooms WHERE id=?", room));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> reservations.create(guest, booking())).getStatusCode().value());
        mvc.perform(get("/api/v1/hotels/" + hotel)).andExpect(status().isNotFound());
        mvc.perform(patch(path + "/status").header("Authorization", bearer("SUPER_ADMIN"))
            .contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
        assertEquals("PENDING_PAYMENT", reservations.create(guest, booking()).status());
    }
    @Test void rolesAndMissingHotelsAreHandledForAllOperations() throws Exception {
        for (String role : new String[]{"USER", "HOTEL_ADMIN", "SUPER_ADMIN", "anonymous"}) {
            for (String verb : new String[]{"GET", "PUT", "PATCH"}) {
                var request = switch (verb) {
                    case "GET" -> get("/api/v1/superadmin/hotels/" + UUID.randomUUID());
                    case "PUT" -> put("/api/v1/superadmin/hotels/" + UUID.randomUUID()).contentType("application/json").content("{\"name\":\"Hotel\",\"address\":\"Address\",\"city\":\"Ica\"}");
                    default -> patch("/api/v1/superadmin/hotels/" + UUID.randomUUID() + "/status").contentType("application/json").content("{\"status\":\"ACTIVE\"}");
                };
                if (!role.equals("anonymous")) request.header("Authorization", bearer(role));
                mvc.perform(request).andExpect(status().is(role.equals("anonymous") ? 401 : role.equals("SUPER_ADMIN") ? 404 : 403));
            }
        }
        mvc.perform(get("/api/v1/superadmin/hotels/invalid").header("Authorization", bearer("SUPER_ADMIN"))).andExpect(status().isBadRequest());
    }
    @Test void bookingWaitsForDeactivationAndThenRejectsInactiveHotel() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var change = executor.submit(() -> new TransactionTemplate(transactionManager).execute(s -> {
                hotels.changeGlobalStatus(hotel, new HotelStatusRequest("INACTIVE")); locked.countDown(); await(release); return true;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var booking = executor.submit(() -> {
                try { reservations.create(guest, booking()); return 201; } catch (ResponseStatusException e) { return e.getStatusCode().value(); }
            });
            assertThrows(TimeoutException.class, () -> booking.get(300, TimeUnit.MILLISECONDS));
            release.countDown(); change.get(10, TimeUnit.SECONDS); assertEquals(404, booking.get(10, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE room_id=?", Long.class, room));
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }
    @Test void completedBookingSurvivesConcurrentDeactivation() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var booking = executor.submit(() -> new TransactionTemplate(transactionManager).execute(s -> {
                var result = reservations.create(guest, booking()); locked.countDown(); await(release); return result;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var change = executor.submit(() -> hotels.changeGlobalStatus(hotel, new HotelStatusRequest("INACTIVE")));
            assertThrows(TimeoutException.class, () -> change.get(300, TimeUnit.MILLISECONDS));
            release.countDown(); var saved = booking.get(10, TimeUnit.SECONDS); change.get(10, TimeUnit.SECONDS);
            assertEquals("PENDING_PAYMENT", jdbc.queryForObject("SELECT status FROM reservations WHERE id=?", String.class, saved.id()));
            assertEquals("INACTIVE", hotels.findGlobal(hotel).status());
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }
    @Test void hotelAdminEditWaitsForStatusChangeAndCannotRestoreOldStatus() throws Exception {
        var executor = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var change = executor.submit(() -> new TransactionTemplate(transactionManager).execute(s -> {
                hotels.changeGlobalStatus(hotel, new HotelStatusRequest("INACTIVE")); locked.countDown(); await(release); return true;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var edit = executor.submit(() -> hotels.updateAssigned(admin, new UpdateHotelRequest("Admin edit", null, "Street", "Ica")));
            assertThrows(TimeoutException.class, () -> edit.get(300, TimeUnit.MILLISECONDS));
            release.countDown(); change.get(10, TimeUnit.SECONDS); edit.get(10, TimeUnit.SECONDS);
            assertEquals("INACTIVE", hotels.findGlobal(hotel).status()); assertEquals("Admin edit", hotels.findGlobal(hotel).name());
        } finally { release.countDown(); executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }
    static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
