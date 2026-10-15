package pe.edu.utp.icastay.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.user.UserEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "icastay.security.jwt.secret=12345678901234567890123456789012"
})
class AdminReservationHttpIntegrationTest {
    private static final String PATH = "/api/v1/admin/hotel/reservations";
    private static final List<String> STATES = List.of("PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED");
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private UUID adminA, adminB, guestA, guestB, hotelA, hotelB, roomA, roomB, other;
    private List<UUID> own;
    private String bearer;

    @BeforeEach
    void setUp() {
        adminA = UUID.randomUUID(); adminB = UUID.randomUUID();
        guestA = UUID.randomUUID(); guestB = UUID.randomUUID();
        hotelA = UUID.randomUUID(); hotelB = UUID.randomUUID();
        roomA = UUID.randomUUID(); roomB = UUID.randomUUID(); other = UUID.randomUUID();
        user(adminA, "HOTEL_ADMIN", "Admin A"); user(adminB, "HOTEL_ADMIN", "Admin B");
        user(guestA, "USER", "Guest A"); user(guestB, "USER", "Guest B");
        fixture(adminA, hotelA, roomA); fixture(adminB, hotelB, roomB);
        own = new ArrayList<>();
        for (String status : STATES) {
            UUID id = UUID.randomUUID(); own.add(id); reservation(id, roomA, guestA, status);
        }
        reservation(other, roomB, guestB, "CONFIRMED");
        bearer = token(adminA, "HOTEL_ADMIN");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM reservations WHERE guest_user_id IN (?, ?)", guestA, guestB);
        jdbc.update("DELETE FROM rooms WHERE hotel_id IN (?, ?)", hotelA, hotelB);
        jdbc.update("DELETE FROM hotels WHERE id IN (?, ?)", hotelA, hotelB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", adminA, adminB, guestA, guestB);
    }

    @Test
    void listUsesOwnHotelAndDefaultsWithoutLeakingOtherGuests() throws Exception {
        var response = get(PATH + "?hotelId=" + hotelB + "&adminUserId=" + adminB, bearer);
        assertEquals(200, response.statusCode());
        var page = json.readTree(response.body());
        assertEquals(0, page.get("page").asInt()); assertEquals(20, page.get("size").asInt());
        assertEquals(4, page.get("totalElements").asInt()); assertEquals(1, page.get("totalPages").asInt());
        assertEquals(Set.copyOf(own.stream().map(UUID::toString).toList()), Set.copyOf(ids(page)));
        assertFalse(response.body().contains(guestB.toString()));
        assertFalse(response.body().contains(other.toString()));
        var b = get(PATH, token(adminB, "HOTEL_ADMIN"));
        assertEquals(List.of(other.toString()), ids(json.readTree(b.body())));
    }

    @Test
    void detailContainsOnlyRequiredFieldsAndHistoricalAmountsEvenWhenRoomInactive() throws Exception {
        UUID id = own.get(1);
        var before = jdbc.queryForMap("SELECT * FROM reservations WHERE id = ?", id);
        jdbc.update("UPDATE rooms SET status = 'INACTIVE', price_per_night = 999, currency = 'USD' WHERE id = ?", roomA);
        jdbc.update("UPDATE hotels SET status = 'INACTIVE' WHERE id = ?", hotelA);
        var response = get(PATH + "/" + id, bearer);
        assertEquals(200, response.statusCode());
        JsonNode detail = json.readTree(response.body());
        Set<String> expected = Set.of("id", "checkIn", "checkOut", "guests", "status", "expiresAt",
                "nightlyPriceSnapshot", "totalAmount", "currency", "createdAt", "roomId", "roomNumber",
                "guestUserId", "guestName", "guestEmail");
        assertEquals(expected.size(), detail.size());
        for (String field : expected) assertTrue(detail.has(field), field);
        assertEquals(id.toString(), detail.get("id").asString());
        assertEquals("2030-01-10", detail.get("checkIn").asString());
        assertEquals("2030-01-13", detail.get("checkOut").asString());
        assertEquals(2, detail.get("guests").asInt());
        assertEquals("CONFIRMED", detail.get("status").asString());
        assertEquals("2029-01-01T00:00:00Z", detail.get("createdAt").asString());
        assertEquals(roomA.toString(), detail.get("roomId").asString());
        assertEquals("101", detail.get("roomNumber").asString());
        assertEquals(guestA.toString(), detail.get("guestUserId").asString());
        assertEquals("Guest A", detail.get("guestName").asString());
        assertEquals(guestA + "@example.test", detail.get("guestEmail").asString());
        assertEquals(0, new BigDecimal("125.50").compareTo(new BigDecimal(detail.get("nightlyPriceSnapshot").asString())));
        assertEquals(0, new BigDecimal("376.50").compareTo(new BigDecimal(detail.get("totalAmount").asString())));
        assertEquals("PEN", detail.get("currency").asString());
        var list = json.readTree(get(PATH, bearer).body());
        for (JsonNode item : list.get("content")) {
            assertEquals(expected.size(), item.size());
            for (String field : expected) assertTrue(item.has(field));
        }
        assertEquals(4, list.get("totalElements").asInt());
        assertEquals(before, jdbc.queryForMap("SELECT * FROM reservations WHERE id = ?", id));
    }

    @Test
    void missingAndForeignReservationReturnNotFound() throws Exception {
        assertEquals(404, get(PATH + "/" + other, bearer).statusCode());
        assertEquals(404, get(PATH + "/" + UUID.randomUUID(), bearer).statusCode());
        assertEquals(400, get(PATH + "/invalid", bearer).statusCode());
    }

    @Test
    void unassignedAdministratorGetsNotFound() throws Exception {
        jdbc.update("DELETE FROM reservations WHERE room_id = ?", roomA);
        jdbc.update("DELETE FROM rooms WHERE id = ?", roomA);
        jdbc.update("DELETE FROM hotels WHERE id = ?", hotelA);
        assertEquals(404, get(PATH, bearer).statusCode());
        assertEquals(404, get(PATH + "/" + other, bearer).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "CONFIRMED", "CANCELLED", "EXPIRED"})
    void filtersPersistedStatusWithoutExpiringPendingReservations(String status) throws Exception {
        var before = jdbc.queryForList("SELECT * FROM reservations WHERE room_id = ? ORDER BY id", roomA);
        var response = get(PATH + "?status=" + status, bearer);
        assertEquals(200, response.statusCode());
        var page = json.readTree(response.body());
        assertEquals(1, page.get("totalElements").asInt());
        assertEquals(status, page.get("content").get(0).get("status").asString());
        if (status.equals("PENDING_PAYMENT")) {
            assertEquals("2020-01-01T00:00:00Z", page.get("content").get(0).get("expiresAt").asString());
        }
        assertEquals(before, jdbc.queryForList("SELECT * FROM reservations WHERE room_id = ? ORDER BY id", roomA));
    }

    @ParameterizedTest
    @CsvSource({
        "from=2030-01-10&to=2030-01-10,4",
        "from=2030-01-12&to=2030-01-12,4",
        "from=2030-01-13&to=2030-01-13,0",
        "from=2030-01-09&to=2030-01-09,0",
        "from=2030-01-09&to=2030-01-10,4",
        "from=2030-01-12&to=2030-01-15,4",
        "from=2030-01-01&to=2030-01-31,4",
        "from=2030-01-13,0", "from=2030-01-12,4",
        "to=2030-01-09,0", "to=2030-01-10,4",
        "from=0001-01-01&to=9999-12-31,4"
    })
    void inclusiveDateRangeRespectsExclusiveCheckout(String query, int count) throws Exception {
        var response = get(PATH + "?" + query, bearer);
        assertEquals(200, response.statusCode());
        assertEquals(count, json.readTree(response.body()).get("totalElements").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "size=1.5",
        "page=2147483647&size=100", "status=OCCUPIED", "status=confirmed", "status=",
        "from=2030-02-30", "to=invalid", "from=2030-01-14&to=2030-01-10",
        "from=", "to=", "from=30-01-10", "from=0000-01-01", "from=2030-1-1"})
    void invalidFiltersReturnBadRequest(String query) throws Exception {
        assertEquals(400, get(PATH + "?" + query, bearer).statusCode());
    }

    @Test
    void paginationIsStableByCreatedAtThenIdDescendingAndCombinesFilters() throws Exception {
        UUID newest = own.get(0);
        jdbc.update("UPDATE reservations SET created_at = '2029-02-01T00:00:00Z' WHERE id = ?", newest);
        List<String> expected = new ArrayList<>(); expected.add(newest.toString());
        expected.addAll(own.stream().filter(id -> !id.equals(newest)).map(UUID::toString).sorted(Comparator.reverseOrder()).toList());
        List<String> actual = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            var response = get(PATH + "?size=2&page=" + page, bearer);
            assertEquals(200, response.statusCode());
            var body = json.readTree(response.body());
            assertEquals(page, body.get("page").asInt()); assertEquals(2, body.get("size").asInt());
            assertEquals(4, body.get("totalElements").asInt()); assertEquals(2, body.get("totalPages").asInt());
            actual.addAll(ids(body));
        }
        assertEquals(expected, actual);
        assertEquals(expected.subList(0, 2), ids(json.readTree(get(PATH + "?size=2", bearer).body())));
        var beyond = json.readTree(get(PATH + "?size=2&page=2", bearer).body());
        assertTrue(ids(beyond).isEmpty()); assertEquals(4, beyond.get("totalElements").asInt());
        var filtered = json.readTree(get(PATH + "?status=CONFIRMED&from=2030-01-11&to=2030-01-12&size=1", bearer).body());
        assertEquals(List.of(own.get(1).toString()), ids(filtered));
        assertEquals(1, filtered.get("totalElements").asInt());
        assertEquals(200, get(PATH + "?size=100", bearer).statusCode());
    }

    @Test
    void emptyHotelReturnsEmptyPage() throws Exception {
        jdbc.update("DELETE FROM reservations WHERE room_id = ?", roomA);
        var response = get(PATH, bearer);
        assertEquals(200, response.statusCode());
        var page = json.readTree(response.body());
        assertTrue(ids(page).isEmpty());
        assertEquals(0, page.get("totalElements").asInt()); assertEquals(0, page.get("totalPages").asInt());
    }

    @Test
    void anonymousAndInvalidJwtReturnUnauthorized() throws Exception {
        for (String token : new String[]{null, "invalid.jwt.token"}) {
            assertEquals(401, get(PATH, token).statusCode());
            assertEquals(401, get(PATH + "/" + own.get(0), token).statusCode());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPER_ADMIN"})
    void onlyHotelAdminMayRead(String role) throws Exception {
        String token = token(adminA, role);
        assertEquals(403, get(PATH, token).statusCode());
        assertEquals(403, get(PATH + "/" + own.get(0), token).statusCode());
    }

    private List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : page.get("content")) ids.add(item.get("id").asString());
        return ids;
    }

    private void user(UUID id, String role, String name) {
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                id, name, id + "@example.test", "not-a-login-credential", role);
    }

    private void fixture(UUID admin, UUID hotel, UUID room) {
        jdbc.update("INSERT INTO hotels (id, admin_user_id, name, address, city) VALUES (?, ?, 'Test', 'Street', 'Ica')", hotel, admin);
        jdbc.update("INSERT INTO rooms (id, hotel_id, number, capacity, price_per_night) VALUES (?, ?, '101', 2, 100.00)", room, hotel);
    }

    private void reservation(UUID id, UUID room, UUID guest, String status) {
        jdbc.update("""
                INSERT INTO reservations (id, guest_user_id, room_id, check_in, check_out, guests,
                    status, expires_at, nightly_price_snapshot, total_amount, currency, created_at)
                VALUES (?, ?, ?, '2030-01-10', '2030-01-13', 2, ?, '2020-01-01T00:00:00Z',
                    125.50, 376.50, 'PEN', '2029-01-01T00:00:00Z')
                """, id, guest, room, status);
    }

    private String token(UUID id, String role) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id); when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role);
        return tokens.createToken(user);
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
