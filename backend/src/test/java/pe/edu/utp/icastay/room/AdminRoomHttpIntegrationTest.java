package pe.edu.utp.icastay.room;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.user.UserEntity;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "icastay.security.jwt.secret=12345678901234567890123456789012"
})
class AdminRoomHttpIntegrationTest {
    private static final String PATH = "/api/v1/admin/hotel/rooms";
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private UUID adminA, adminB, guest, hotelA, hotelB, roomA, roomB;
    private String tokenA, tokenB, guestToken;

    @BeforeEach
    void setUp() {
        adminA = UUID.randomUUID(); adminB = UUID.randomUUID(); guest = UUID.randomUUID();
        hotelA = UUID.randomUUID(); hotelB = UUID.randomUUID();
        roomA = UUID.randomUUID(); roomB = UUID.randomUUID();
        user(adminA, "HOTEL_ADMIN"); user(adminB, "HOTEL_ADMIN"); user(guest, "USER");
        fixture(adminA, hotelA, roomA); fixture(adminB, hotelB, roomB);
        tokenA = token(adminA, "HOTEL_ADMIN"); tokenB = token(adminB, "HOTEL_ADMIN");
        guestToken = token(guest, "USER");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM reservations WHERE guest_user_id = ?", guest);
        jdbc.update("DELETE FROM rooms WHERE hotel_id IN (?, ?)", hotelA, hotelB);
        jdbc.update("DELETE FROM hotels WHERE id IN (?, ?)", hotelA, hotelB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?)", adminA, adminB, guest);
    }

    @Test
    void createsActiveRoomWithDefaultCurrencyAndIgnoresOwnershipFields() throws Exception {
        var body = details("102");
        body.put("id", roomB); body.put("hotelId", hotelB); body.put("adminUserId", adminB);
        body.put("status", "INACTIVE");
        var response = call("POST", PATH, tokenA, body);
        assertEquals(201, response.statusCode());
        UUID id = UUID.fromString(json.readTree(response.body()).get("id").asString());
        assertEquals(PATH + "/" + id, response.headers().firstValue("Location").orElseThrow());
        var saved = row(id);
        assertEquals(hotelA, saved.get("hotel_id"));
        assertEquals("ACTIVE", saved.get("status"));
        assertEquals("PEN", saved.get("currency"));
        assertEquals(2, saved.get("capacity"));
        assertEquals(new BigDecimal("150.25"), saved.get("price_per_night"));
        assertEquals("101", row(roomB).get("number"));
    }

    @Test
    void listsOwnActiveAndInactiveRoomsAndGetsOwnedId() throws Exception {
        assertEquals(200, call("PATCH", PATH + "/" + roomA + "/status", tokenA, Map.of("status", "INACTIVE")).statusCode());
        var response = call("GET", PATH + "?hotelId=" + hotelB, tokenA, null);
        assertEquals(200, response.statusCode());
        var list = json.readTree(response.body());
        assertEquals(1, list.size());
        assertEquals(roomA.toString(), list.get(0).get("id").asString());
        assertEquals("INACTIVE", list.get(0).get("status").asString());
        assertEquals(200, call("GET", PATH + "/" + roomA, tokenA, null).statusCode());
        assertEquals(200, call("GET", PATH + "/" + roomB, tokenB, null).statusCode());
        jdbc.update("DELETE FROM rooms WHERE id = ?", roomA);
        assertEquals("[]", call("GET", PATH, tokenA, null).body());
    }

    @Test
    void updatesEditableFieldsWithoutMovingRoomOrChangingStatus() throws Exception {
        var beforeOther = row(roomB);
        call("PATCH", PATH + "/" + roomA + "/status", tokenA, Map.of("status", "INACTIVE"));
        var body = details("201");
        body.put("description", "Nueva descripción"); body.put("capacity", 4);
        body.put("pricePerNight", new BigDecimal("225.50")); body.put("currency", "USD");
        body.put("hotelId", hotelB); body.put("id", roomB); body.put("status", "ACTIVE");
        assertEquals(200, call("PUT", PATH + "/" + roomA, tokenA, body).statusCode());
        var saved = row(roomA);
        assertEquals("201", saved.get("number"));
        assertEquals("Nueva descripción", saved.get("description"));
        assertEquals(4, saved.get("capacity"));
        assertEquals(new BigDecimal("225.50"), saved.get("price_per_night"));
        assertEquals("USD", saved.get("currency"));
        assertEquals(hotelA, saved.get("hotel_id"));
        assertEquals("INACTIVE", saved.get("status"));
        assertEquals(beforeOther, row(roomB));
    }

    @Test
    void duplicateNumberReturnsConflictOnCreateAndUpdateButIsAllowedInAnotherHotel() throws Exception {
        assertEquals(409, call("POST", PATH, tokenA, details("101")).statusCode());
        var created = call("POST", PATH, tokenA, details("102"));
        UUID id = UUID.fromString(json.readTree(created.body()).get("id").asString());
        var before = row(id);
        assertEquals(409, call("PUT", PATH + "/" + id, tokenA, details("101")).statusCode());
        assertEquals(before, row(id));
        assertEquals(200, call("PUT", PATH + "/" + id, tokenA, details("102")).statusCode());
        assertEquals(201, call("POST", PATH, tokenB, details("102")).statusCode());
    }

    @Test
    void concurrentDuplicateCreationReturnsOneCreatedAndOneConflict() throws Exception {
        var request = request("POST", PATH, tokenA, json.writeValueAsString(details("102")));
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var codes = List.of(first.get(15, TimeUnit.SECONDS).statusCode(), second.get(15, TimeUnit.SECONDS).statusCode());
        assertTrue(codes.contains(201)); assertTrue(codes.contains(409));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM rooms WHERE hotel_id = ? AND number = '102'", Integer.class, hotelA));
    }

    @ParameterizedTest
    @ValueSource(strings = {"number", "capacity", "pricePerNight"})
    void requiredFieldsRejectMissingAndNullOnCreateAndUpdate(String field) throws Exception {
        var body = details("102"); body.remove(field);
        assertInvalidDetails(body);
        body.put(field, null);
        assertInvalidDetails(body);
    }

    @Test
    void rejectsInvalidValuesWithoutMutation() throws Exception {
        for (Object value : List.of("", " ", " 102", "102 ", "x".repeat(41))) {
            var body = details("102"); body.put("number", value); assertInvalidDetails(body);
        }
        for (Object value : List.of(0, -1, new BigDecimal("1.5"), new BigDecimal("2147483648"))) {
            var body = details("102"); body.put("capacity", value); assertInvalidDetails(body);
        }
        for (Object value : List.of(0, -1, new BigDecimal("1.001"), new BigDecimal("10000000000"))) {
            var body = details("102"); body.put("pricePerNight", value); assertInvalidDetails(body);
        }
        for (String value : List.of("", "pen", "SOLES", "ZZZ")) {
            var body = details("102"); body.put("currency", value); assertInvalidDetails(body);
        }
    }

    @Test
    void acceptsBoundaryValuesAndOptionalDescription() throws Exception {
        var body = details("x".repeat(40)); body.put("capacity", Integer.MAX_VALUE);
        body.put("pricePerNight", new BigDecimal("9999999999.99"));
        assertEquals(201, call("POST", PATH, tokenA, body).statusCode());
        body = details("101"); body.put("description", null); body.put("pricePerNight", new BigDecimal("0.01"));
        assertEquals(200, call("PUT", PATH + "/" + roomA, tokenA, body).statusCode());
        assertNull(row(roomA).get("description"));
    }

    @Test
    void rejectsMalformedRequestsAndInvalidStatus() throws Exception {
        var before = row(roomA);
        for (String body : List.of("{", "null", "", "{}")) {
            assertEquals(400, send("POST", PATH, tokenA, body).statusCode());
            assertEquals(400, send("PUT", PATH + "/" + roomA, tokenA, body).statusCode());
            assertEquals(400, send("PATCH", PATH + "/" + roomA + "/status", tokenA, body).statusCode());
        }
        for (String status : List.of("OCCUPIED", "active", "", " ACTIVE ")) {
            assertEquals(400, call("PATCH", PATH + "/" + roomA + "/status", tokenA, Map.of("status", status)).statusCode());
        }
        assertEquals(before, row(roomA));
    }

    @Test
    void otherHotelAndMissingIdsReturnNotFoundForAllItemOperations() throws Exception {
        var otherBefore = row(roomB);
        for (UUID id : List.of(roomB, UUID.randomUUID())) {
            assertEquals(404, call("GET", PATH + "/" + id, tokenA, null).statusCode());
            assertEquals(404, call("PUT", PATH + "/" + id, tokenA, details("102")).statusCode());
            assertEquals(404, call("PATCH", PATH + "/" + id + "/status", tokenA, Map.of("status", "INACTIVE")).statusCode());
        }
        assertEquals(otherBefore, row(roomB));
    }

    @Test
    void administratorWithoutHotelGetsNotFoundOnEveryEndpoint() throws Exception {
        jdbc.update("DELETE FROM rooms WHERE hotel_id = ?", hotelA);
        jdbc.update("DELETE FROM hotels WHERE id = ?", hotelA);
        assertAllEndpoints(tokenA, 404);
    }

    @Test
    void anonymousAndInvalidTokenGetUnauthorizedOnEveryEndpoint() throws Exception {
        assertAllEndpoints(null, 401);
        assertAllEndpoints("invalid.jwt.token", 401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPER_ADMIN"})
    void otherRolesGetForbiddenOnEveryEndpoint(String role) throws Exception {
        jdbc.update("UPDATE users SET role=? WHERE id=?", role, adminA);
        assertAllEndpoints(token(adminA, role), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "CONFIRMED"})
    void preservesReservationsAndSnapshotsAndPreventsBookingInactiveRoom(String reservationStatus) throws Exception {
        var response = call("POST", "/api/v1/reservations", guestToken, booking(10));
        assertEquals(201, response.statusCode());
        UUID reservation = UUID.fromString(json.readTree(response.body()).get("id").asString());
        jdbc.update("UPDATE reservations SET status = ? WHERE id = ?", reservationStatus, reservation);
        var before = jdbc.queryForMap("SELECT * FROM reservations WHERE id = ?", reservation);
        var update = details("101"); update.put("pricePerNight", new BigDecimal("250.00"));
        update.put("currency", "USD"); update.put("capacity", 1);
        assertEquals(200, call("PUT", PATH + "/" + roomA, tokenA, update).statusCode());
        assertEquals(200, call("PATCH", PATH + "/" + roomA + "/status", tokenA, Map.of("status", "INACTIVE")).statusCode());
        assertEquals(before, jdbc.queryForMap("SELECT * FROM reservations WHERE id = ?", reservation));
        assertEquals(404, call("POST", "/api/v1/reservations", guestToken, booking(20)).statusCode());
        assertEquals("[]", call("GET", "/api/v1/hotels/" + hotelA + "/rooms", null, null).body());
        assertEquals(200, call("PATCH", PATH + "/" + roomA + "/status", tokenA, Map.of("status", "ACTIVE")).statusCode());
        var nextBooking = booking(20); nextBooking.put("guests", 1);
        var next = call("POST", "/api/v1/reservations", guestToken, nextBooking);
        assertEquals(201, next.statusCode());
        assertEquals(0, new BigDecimal("250.00").compareTo(
                new BigDecimal(json.readTree(next.body()).get("nightlyPriceSnapshot").asString())));
        assertEquals("USD", json.readTree(next.body()).get("currency").asString());
        assertEquals(before, jdbc.queryForMap("SELECT * FROM reservations WHERE id = ?", reservation));
    }

    @Test
    void concurrentOverlappingReservationsCannotBothSucceed() throws Exception {
        var request = request("POST", "/api/v1/reservations", guestToken, json.writeValueAsString(booking(10)));
        CompletableFuture<HttpResponse<String>> first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        var codes = List.of(first.get(15, TimeUnit.SECONDS).statusCode(), second.get(15, TimeUnit.SECONDS).statusCode());
        assertTrue(codes.contains(201)); assertTrue(codes.contains(409));
    }

    private void assertInvalidDetails(Map<String, Object> body) throws Exception {
        var before = row(roomA);
        assertEquals(400, call("POST", PATH, tokenA, body).statusCode());
        assertEquals(400, call("PUT", PATH + "/" + roomA, tokenA, body).statusCode());
        assertEquals(before, row(roomA));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM rooms WHERE hotel_id = ?", Integer.class, hotelA));
    }

    private void assertAllEndpoints(String bearer, int status) throws Exception {
        assertEquals(status, call("GET", PATH, bearer, null).statusCode());
        assertEquals(status, call("GET", PATH + "/" + roomA, bearer, null).statusCode());
        assertEquals(status, call("POST", PATH, bearer, details("102")).statusCode());
        assertEquals(status, call("PUT", PATH + "/" + roomA, bearer, details("102")).statusCode());
        assertEquals(status, call("PATCH", PATH + "/" + roomA + "/status", bearer, Map.of("status", "INACTIVE")).statusCode());
    }

    private Map<String, Object> booking(int days) {
        LocalDate checkIn = LocalDate.now().plusDays(days);
        return new HashMap<>(Map.of("roomId", roomA, "checkIn", checkIn.toString(),
                "checkOut", checkIn.plusDays(2).toString(), "guests", 2));
    }

    private Map<String, Object> details(String number) {
        return new HashMap<>(Map.of("number", number, "capacity", 2, "pricePerNight", new BigDecimal("150.25")));
    }

    private void user(UUID id, String role) {
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                id, "HTTP room test", id + "@example.test", "not-a-login-credential", role);
    }

    private void fixture(UUID admin, UUID hotel, UUID room) {
        jdbc.update("INSERT INTO hotels (id, admin_user_id, name, address, city) VALUES (?, ?, ?, ?, ?)",
                hotel, admin, "HTTP room hotel", "Street", "Ica");
        jdbc.update("INSERT INTO rooms (id, hotel_id, number, capacity, price_per_night) VALUES (?, ?, '101', 2, 100.00)", room, hotel);
    }

    private String token(UUID id, String role) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id); when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role);
        return tokens.createToken(user);
    }

    private Map<String, Object> row(UUID id) { return jdbc.queryForMap("SELECT * FROM rooms WHERE id = ?", id); }

    private HttpResponse<String> call(String method, String path, String bearer, Object body) throws Exception {
        return send(method, path, bearer, body == null ? null : json.writeValueAsString(body));
    }

    private HttpResponse<String> send(String method, String path, String bearer, String body) throws Exception {
        return client.send(request(method, path, bearer, body), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String bearer, String body) {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        if (body != null) builder.header("Content-Type", "application/json");
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
