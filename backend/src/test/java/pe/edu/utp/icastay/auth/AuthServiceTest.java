package pe.edu.utp.icastay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    void registroValidoNormalizaDatosCodificaBCryptYDevuelveUsuarioPublico() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        UserEntity savedUser = mockUser("Usuario de Prueba", "nuevo@icastay.com", "bcrypt-hash", "USER");
        when(users.existsByEmail("nuevo@icastay.com")).thenReturn(false);
        when(passwordEncoder.encode("ClaveSegura123!")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(UserEntity.class))).thenReturn(savedUser);

        RegisterResponse response = new AuthService(users, passwordEncoder, jwtTokenService)
                .register(new RegisterRequest(" Usuario de Prueba ", " Nuevo@IcaStay.com ", "ClaveSegura123!"));

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).saveAndFlush(userCaptor.capture());
        UserEntity user = userCaptor.getValue();
        assertEquals("Usuario de Prueba", user.getName());
        assertEquals("nuevo@icastay.com", user.getEmail());
        assertEquals("bcrypt-hash", user.getPasswordHash());
        assertEquals("USER", user.getRole());
        assertEquals("ACTIVE", user.getStatus());
        assertEquals("Usuario de Prueba", response.name());
        assertEquals("nuevo@icastay.com", response.email());
        assertEquals("USER", response.role());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void registroRechazaCorreoDuplicadoSinDistinguirMayusculas() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(users.existsByEmail("nuevo@icastay.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new AuthService(users, passwordEncoder, jwtTokenService)
                        .register(new RegisterRequest("Usuario", "NUEVO@ICASTAY.COM", "ClaveSegura123!")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(passwordEncoder, jwtTokenService);
    }

    @Test
    void registroConvierteConflictoDeUnicidadConcurrenteEn409() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(users.existsByEmail("nuevo@icastay.com")).thenReturn(false);
        when(passwordEncoder.encode("ClaveSegura123!")).thenReturn("bcrypt-hash");
        when(users.saveAndFlush(any(UserEntity.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new AuthService(users, passwordEncoder, jwtTokenService)
                        .register(new RegisterRequest("Usuario", "nuevo@icastay.com", "ClaveSegura123!")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void registroRechazaCamposObligatorios() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        AuthService service = new AuthService(users, passwordEncoder, jwtTokenService);

        assertBadRequest(() -> service.register(null));
        assertBadRequest(() -> service.register(new RegisterRequest(null, "user@example.com", "secret123")));
        assertBadRequest(() -> service.register(new RegisterRequest("Usuario", null, "secret123")));
        assertBadRequest(() -> service.register(new RegisterRequest("Usuario", "user@example.com", null)));
        verifyNoInteractions(users, passwordEncoder, jwtTokenService);
    }

    @Test
    void registroRechazaEmailInvalidoLongitudesExcesivasYPasswordCorto() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        AuthService service = new AuthService(users, passwordEncoder, jwtTokenService);

        assertBadRequest(() -> service.register(new RegisterRequest("Usuario", "correo-invalido", "secret123")));
        assertBadRequest(() -> service.register(new RegisterRequest("a".repeat(161), "user@example.com", "secret123")));
        assertBadRequest(() -> service.register(new RegisterRequest("Usuario", "a".repeat(243) + "@example.com", "secret123")));
        assertBadRequest(() -> service.register(new RegisterRequest("Usuario", "user@example.com", "1234567")));
        verifyNoInteractions(users, passwordEncoder, jwtTokenService);
    }

    @Test
    void passwordBCryptRealNoSeAlmacenaEnTextoPlano() {
        AuthUserRepository users = mock(AuthUserRepository.class);
        PasswordEncoder passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        UserEntity savedUser = mockUser("Usuario", "user@example.com", "hash-devuelto", "USER");
        when(users.existsByEmail("user@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(UserEntity.class))).thenReturn(savedUser);

        new AuthService(users, passwordEncoder, jwtTokenService)
                .register(new RegisterRequest("Usuario", "user@example.com", "ClaveSegura123!"));

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).saveAndFlush(userCaptor.capture());
        String hash = userCaptor.getValue().getPasswordHash();
        assertTrue(hash.startsWith("$2"));
        assertTrue(passwordEncoder.matches("ClaveSegura123!", hash));
    }

    private UserEntity mockUser() {
        return mockUser("User", "user@example.com", "bcrypt-hash", "USER");
    }

    private UserEntity mockUser(String name, String email, String passwordHash, String role) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(user.getName()).thenReturn(name);
        when(user.getEmail()).thenReturn(email);
        when(user.getPasswordHash()).thenReturn(passwordHash);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private void assertBadRequest(Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
