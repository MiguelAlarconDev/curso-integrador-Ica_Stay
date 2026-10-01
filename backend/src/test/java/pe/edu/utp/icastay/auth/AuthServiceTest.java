package pe.edu.utp.icastay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.user.UserEntity;

class AuthServiceTest {

    @Test
    void loginValidoNormalizaEmailVerificaBCryptYDevuelveBearerToken() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        UserEntity user = mockUser();
        when(users.findByEmailAndStatus("user@example.com", "ACTIVE")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(true);
        when(jwtTokenService.createToken(user)).thenReturn("jwt-token");

        LoginResponse response = new AuthService(users, passwordEncoder, jwtTokenService)
                .login(new LoginRequest(" User@Example.com ", "secret"));

        assertEquals("jwt-token", response.token());
        assertEquals("Bearer", response.tokenType());
        verify(users).findByEmailAndStatus("user@example.com", "ACTIVE");
        verify(passwordEncoder).matches("secret", "bcrypt-hash");
    }

    @Test
    void loginConUsuarioInexistenteOInactivoDevuelve401() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(users.findByEmailAndStatus("user@example.com", "ACTIVE")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new AuthService(users, passwordEncoder, jwtTokenService)
                        .login(new LoginRequest("user@example.com", "secret")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void loginConPasswordIncorrectoDevuelve401() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        UserEntity user = mockUser();
        when(users.findByEmailAndStatus("user@example.com", "ACTIVE")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-secret", "bcrypt-hash")).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new AuthService(users, passwordEncoder, jwtTokenService)
                        .login(new LoginRequest("user@example.com", "bad-secret")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(jwtTokenService);
    }

    private UserEntity mockUser() {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.getPasswordHash()).thenReturn("bcrypt-hash");
        when(user.getRole()).thenReturn("USER");
        return user;
    }
}
