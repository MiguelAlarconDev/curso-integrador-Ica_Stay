package pe.edu.utp.icastay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pe.edu.utp.icastay.user.UserEntity;

class JwtTokenServiceTest {
    private static final String SECRET = "12345678901234567890123456789012";
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    @Test
    void creaYValidaTokenFirmado() {
        JwtTokenService jwtTokenService = new JwtTokenService(
                SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

        String token = jwtTokenService.createToken(mockUser());
        Map<String, Object> claims = jwtTokenService.validateAndReadClaims(token);

        assertEquals("user@example.com", claims.get("sub"));
        assertEquals("11111111-1111-1111-1111-111111111111", claims.get("userId"));
        assertEquals("USER", claims.get("role"));
        assertEquals(1790856000, ((Number) claims.get("iat")).longValue());
        assertEquals(1790859600, ((Number) claims.get("exp")).longValue());
    }

    @Test
    void rechazaTokenAlterado() {
        JwtTokenService jwtTokenService = new JwtTokenService(
                SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = jwtTokenService.createToken(mockUser());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(IllegalArgumentException.class, () -> jwtTokenService.validateAndReadClaims(tampered));
    }

    @Test
    void requiereSecretoSuficiente() {
        JwtTokenService jwtTokenService = new JwtTokenService(
                "short", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> jwtTokenService.createToken(mockUser()));
    }

    private UserEntity mockUser() {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getRole()).thenReturn("USER");
        return user;
    }
}
