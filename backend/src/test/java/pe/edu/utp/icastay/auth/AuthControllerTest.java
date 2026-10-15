package pe.edu.utp.icastay.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthControllerTest {

    @Test
    void delegaLoginAlService() {
        AuthService authService = mock(AuthService.class);
        LoginRequest request = new LoginRequest("user@example.com", "secret");
        LoginResponse expected = new LoginResponse("jwt-token", "Bearer");
        when(authService.login(request)).thenReturn(expected);

        LoginResponse response = new AuthController(authService).login(request);

        assertSame(expected, response);
        verify(authService).login(request);
    }

    @Test
    void delegaRegistroAlService() {
        AuthService authService = mock(AuthService.class);
        RegisterRequest request = new RegisterRequest("Usuario", "user@example.com", "secret123");
        RegisterResponse expected = new RegisterResponse(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Usuario",
                "user@example.com",
                "USER");
        when(authService.register(request)).thenReturn(expected);

        RegisterResponse response = new AuthController(authService).register(request);

        assertSame(expected, response);
        verify(authService).register(request);
    }
}
