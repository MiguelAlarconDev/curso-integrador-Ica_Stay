package pe.edu.utp.icastay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

// Real HTTP is required: MockMvc does not perform the container's ERROR redispatch.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "icastay.security.jwt.secret=12345678901234567890123456789012"
})
class AuthHttpIntegrationTest {
    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AuthUserRepository users;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private String email;

    @BeforeEach
    void setUp() {
        email = "http-auth-" + UUID.randomUUID() + "@example.test";
    }

    @AfterEach
    void cleanUp() {
        // HTTP transactions cannot be rolled back by a transaction on the test thread.
        users.findByEmailAndStatus(email, "ACTIVE").ifPresent(users::delete);
    }

    @Test
    void registrationAndLoginWorkWithoutAuthentication() throws Exception {
        assertEquals(201, post("/api/v1/auth/register", registration(email)).statusCode());
        HttpResponse<String> response = post("/api/v1/auth/login", """
                {"email":"%s","password":"HttpTestPassword123!"}
                """.formatted(email));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"token\""));
        assertTrue(response.body().contains("\"tokenType\":\"Bearer\""));
    }

    @Test
    void duplicateEmailReturnsConflictWithoutAuthentication() throws Exception {
        assertEquals(201, post("/api/v1/auth/register", registration(email)).statusCode());
        assertEquals(409, post("/api/v1/auth/register",
                registration(email.toUpperCase(Locale.ROOT))).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":\"Test\",\"email\":\"invalid\",\"password\":\"HttpTestPassword123!\"}",
            "{\"name\":\"Test\",\"email\":\"invalid-test@example.test\",\"password\":\"short\"}",
            "{"
    })
    void invalidRegistrationReturnsBadRequestWithoutAuthentication(String body) throws Exception {
        assertEquals(400, post("/api/v1/auth/register", body).statusCode());
    }

    @Test
    void incorrectCredentialsReturnUnauthorized() throws Exception {
        assertEquals(201, post("/api/v1/auth/register", registration(email)).statusCode());
        assertEquals(401, post("/api/v1/auth/login", """
                {"email":"%s","password":"incorrect"}
                """.formatted(email)).statusCode());
    }

    @Test
    void protectedEndpointStillRequiresAuthentication() throws Exception {
        assertEquals(401, post("/api/v1/reservations", "{}").statusCode());
    }

    @Test
    void directRequestToErrorStillRequiresAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/error"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private String registration(String address) {
        return """
                {"name":"HTTP Test","email":"%s","password":"HttpTestPassword123!"}
                """.formatted(address);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
