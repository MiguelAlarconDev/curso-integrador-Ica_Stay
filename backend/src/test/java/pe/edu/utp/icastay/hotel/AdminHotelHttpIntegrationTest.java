package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
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
class AdminHotelHttpIntegrationTest {
    private static final String PATH = "/api/v1/admin/hotel";
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtTokenService tokens;
    private final JsonMapper json = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private UUID adminA, adminB, hotelA, hotelB;
    private String tokenA, tokenB;

    @BeforeEach
    void setUp() {
        adminA = UUID.randomUUID();
        adminB = UUID.randomUUID();
        hotelA = UUID.randomUUID();
        hotelB = UUID.randomUUID();
        createFixture(adminA, hotelA, "Hotel A");
        createFixture(adminB, hotelB, "Hotel B");
        tokenA = token(adminA, "HOTEL_ADMIN");
        tokenB = token(adminB, "HOTEL_ADMIN");
    }

    @AfterEach
    void cleanUp() {
        // HTTP commits are outside the test thread; remove only this test's fixtures.
        jdbc.update("DELETE FROM hotels WHERE id IN (?, ?)", hotelA, hotelB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", adminA, adminB);
    }

    @Test
    void eachAdministratorReadsOnlyTheirAssignedHotel() throws Exception {
        var a = request("GET", PATH + "?adminUserId=" + adminB + "&hotelId=" + hotelB, tokenA, null);
        assertEquals(200, a.statusCode());
        assertEquals(hotelA.toString(), json.readTree(a.body()).get("id").asString());
        var b = request("GET", PATH, tokenB, null);
        assertEquals(200, b.statusCode());
        assertEquals(hotelB.toString(), json.readTree(b.body()).get("id").asString());
    }

    @Test
    void updatePersistsOnlyEditableFieldsOfOwnHotel() throws Exception {
        var otherBefore = row(hotelB);
        String body = json.writeValueAsString(Map.of(
                "name", "  Hotel renovado  ", "description", "  Nueva descripción  ",
                "address", "  Calle nueva  ", "city", "  Paracas  ",
                "id", hotelB, "adminUserId", adminB, "admin_user_id", adminB, "status", "INACTIVE"));
        var response = request("PUT", PATH + "?hotelId=" + hotelB, tokenA, body);
        assertEquals(200, response.statusCode());
        assertEquals(hotelA.toString(), json.readTree(response.body()).get("id").asString());
        var own = row(hotelA);
        assertEquals("Hotel renovado", own.get("name"));
        assertEquals("Nueva descripción", own.get("description"));
        assertEquals("Calle nueva", own.get("address"));
        assertEquals("Paracas", own.get("city"));
        assertEquals(adminA, own.get("admin_user_id"));
        assertEquals("ACTIVE", own.get("status"));
        assertEquals(otherBefore, row(hotelB));
        assertEquals(response.body(), request("GET", PATH, tokenA, null).body());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUPER_ADMIN"})
    void otherRolesCannotReadOrUpdate(String role) throws Exception {
        var before = row(hotelA);
        String wrongRole = token(adminA, role);
        assertEquals(403, request("GET", PATH, wrongRole, null).statusCode());
        assertEquals(403, request("PUT", PATH, wrongRole, validBody()).statusCode());
        assertEquals(before, row(hotelA));
    }

    @Test
    void anonymousAndInvalidBearerCannotReadOrUpdate() throws Exception {
        var before = row(hotelA);
        for (String bearer : new String[]{null, "invalid.jwt.token"}) {
            assertEquals(401, request("GET", PATH, bearer, null).statusCode());
            assertEquals(401, request("PUT", PATH, bearer, validBody()).statusCode());
        }
        assertEquals(before, row(hotelA));
    }

    @Test
    void unassignedAdministratorGetsNotFound() throws Exception {
        jdbc.update("DELETE FROM hotels WHERE id = ?", hotelA);
        assertEquals(404, request("GET", PATH, tokenA, null).statusCode());
        assertEquals(404, request("PUT", PATH, tokenA, validBody()).statusCode());
        assertEquals("Hotel B", row(hotelB).get("name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "address", "city"})
    void requiredFieldsRejectMissingNullBlankAndOverlongValues(String field) throws Exception {
        var before = row(hotelA);
        int max = switch (field) { case "name" -> 180; case "address" -> 255; default -> 120; };
        for (String value : new String[]{null, "", "   ", "x".repeat(max + 1)}) {
            var body = new java.util.HashMap<String, Object>(Map.of("name", "Changed", "address", "Street", "city", "Ica"));
            body.put(field, value);
            assertEquals(400, request("PUT", PATH, tokenA, json.writeValueAsString(body)).statusCode());
            assertEquals(before, row(hotelA));
        }
        var missing = new java.util.HashMap<String, Object>(Map.of("name", "Changed", "address", "Street", "city", "Ica"));
        missing.remove(field);
        assertEquals(400, request("PUT", PATH, tokenA, json.writeValueAsString(missing)).statusCode());
        assertEquals(before, row(hotelA));
    }

    @Test
    void invalidBodyAndLongDescriptionDoNotPersistPartialUpdates() throws Exception {
        var before = row(hotelA);
        for (String body : new String[]{"", "{", "null", "{}", json.writeValueAsString(Map.of(
                "name", "Changed", "address", "Street", "city", "Ica", "description", "x".repeat(5001)))}) {
            assertEquals(400, request("PUT", PATH, tokenA, body).statusCode());
            assertEquals(before, row(hotelA));
        }
    }

    @Test
    void acceptsMaximumLengthsAndOptionalDescription() throws Exception {
        String body = json.writeValueAsString(Map.of("name", "n".repeat(180), "address", "a".repeat(255),
                "city", "c".repeat(120), "description", "d".repeat(5000)));
        assertEquals(200, request("PUT", PATH, tokenA, body).statusCode());
        assertEquals(5000, row(hotelA).get("description").toString().length());
        for (String description : new String[]{"", ",\"description\":null", ",\"description\":\"   \""}) {
            assertEquals(200, request("PUT", PATH, tokenA,
                    "{\"name\":\"Hotel\",\"address\":\"Street\",\"city\":\"Ica\"" + description + "}").statusCode());
            assertNull(row(hotelA).get("description"));
        }
    }

    @Test
    void inactiveHotelCanBeEditedButRemainsAbsentFromPublicDetail() throws Exception {
        assertEquals(200, request("GET", "/api/v1/hotels/" + hotelA, null, null).statusCode());
        jdbc.update("UPDATE hotels SET status = 'INACTIVE' WHERE id = ?", hotelA);
        assertEquals(200, request("GET", PATH, tokenA, null).statusCode());
        assertEquals(200, request("PUT", PATH, tokenA, validBody()).statusCode());
        assertEquals("INACTIVE", row(hotelA).get("status"));
        assertEquals(404, request("GET", "/api/v1/hotels/" + hotelA, null, null).statusCode());
    }

    private void createFixture(UUID admin, UUID hotel, String name) {
        jdbc.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, 'HOTEL_ADMIN')",
                admin, "HTTP hotel test", admin + "@example.test", "not-a-login-credential");
        jdbc.update("INSERT INTO hotels (id, admin_user_id, name, description, address, city) VALUES (?, ?, ?, ?, ?, ?)",
                hotel, admin, name, "Original", "Original street", "Ica");
    }

    private String token(UUID id, String role) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role);
        return tokens.createToken(user);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM hotels WHERE id = ?", id);
    }

    private String validBody() {
        return "{\"name\":\"Updated\",\"address\":\"Street\",\"city\":\"Ica\",\"description\":\"Updated description\"}";
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
