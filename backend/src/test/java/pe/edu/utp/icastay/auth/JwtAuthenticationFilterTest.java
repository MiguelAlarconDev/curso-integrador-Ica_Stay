package pe.edu.utp.icastay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void autenticaBearerTokenValido() throws ServletException, IOException {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.validateAndReadClaims("valid-token"))
                .thenReturn(Map.of(
                        "sub", "user@example.com",
                        "userId", "11111111-1111-1111-1111-111111111111",
                        "role", "USER"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        new JwtAuthenticationFilter(jwtTokenService)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        AuthenticatedUser principal = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), principal.id());
        assertEquals("user@example.com", principal.email());
        assertEquals("USER", principal.role());
    }

    @Test
    void ignoraTokenInvalido() throws ServletException, IOException {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.validateAndReadClaims("bad-token"))
                .thenThrow(new IllegalArgumentException("JWT invalido"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");

        new JwtAuthenticationFilter(jwtTokenService)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
