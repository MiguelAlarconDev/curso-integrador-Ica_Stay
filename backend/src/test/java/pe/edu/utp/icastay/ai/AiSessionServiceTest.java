package pe.edu.utp.icastay.ai;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AiSessionServiceTest {
    private final Instant now = Instant.parse("2026-10-22T12:00:00Z");
    private final String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    private AiSessionService service(Instant time) {
        return new AiSessionService(key, false, "http://localhost:4200", Clock.fixed(time, ZoneOffset.UTC));
    }
    static String value(String setCookie) { return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';')); }

    @Test void createsRandomSecretsAndSignsAllFields() {
        var service = service(now); var one = service.create(); var two = service.create();
        assertEquals(32, Base64.getUrlDecoder().decode(one.secret()).length);
        assertFalse(one.secret().equals(two.secret())); assertFalse(one.conversationId().equals(two.conversationId()));
        String cookie = service.cookie(one);
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api/v1/ai")); assertFalse(cookie.contains("Domain="));
        assertFalse(cookie.contains("Secure")); assertTrue(cookie.contains("Max-Age=2592000"));
        assertTrue(service.decode(value(cookie)).orElseThrow().secret().equals(one.secret()));
        assertEquals("AiSession[omitida]", one.toString());
    }

    @Test void rejectsMissingMalformedManipulatedAndExpiredCookies() {
        var service = service(now); String cookie = value(service.cookie(service.create()));
        assertTrue(service.decode(null).isEmpty()); assertTrue(service.decode("malformed").isEmpty());
        String[] parts = cookie.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            var manipulated = parts.clone(); manipulated[i] = "modified";
            assertTrue(service.decode(String.join(".", manipulated)).isEmpty());
        }
        assertTrue(service(now.plus(Duration.ofDays(30))).decode(cookie).isEmpty());
        assertTrue(service(now.plus(Duration.ofDays(29))).decode(cookie).isPresent());
    }

    @Test void productionRequiresPersistentSigningKeyAndSecureCookie() {
        assertThrows(IllegalArgumentException.class, () -> new AiSessionService("", true, "https://icastay.example"));
        assertThrows(IllegalArgumentException.class, () -> new AiSessionService(key, false, "https://icastay.example"));
        assertThrows(IllegalArgumentException.class, () -> new AiSessionService("short", true, "http://localhost:4200"));
        var production = new AiSessionService(key, true, "https://icastay.example");
        assertTrue(production.cookie(production.create()).contains("Secure"));
    }

    @Test void rejectsDuplicateCookiesAndLocalRestartInvalidatesEphemeralSessions() {
        var service = service(now); var request = new org.springframework.mock.web.MockHttpServletRequest();
        var cookie = new jakarta.servlet.http.Cookie(AiSessionService.COOKIE_NAME, value(service.cookie(service.create())));
        request.setCookies(cookie, cookie); assertTrue(service.read(request).isEmpty());
        var first = new AiSessionService("", false, "http://localhost:4200");
        var restarted = new AiSessionService("", false, "http://localhost:4200");
        assertTrue(restarted.decode(value(first.cookie(first.create()))).isEmpty());
    }
}
