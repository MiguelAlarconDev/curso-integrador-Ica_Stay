package pe.edu.utp.icastay.auth;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.user.UserEntity;

@Service
public class AuthService {
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final AuthUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AuthUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserEntity user = users.findByEmailAndStatus(email, ACTIVE_STATUS)
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new LoginResponse(jwtTokenService.createToken(user), "Bearer");
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw invalidCredentials();
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciales invalidas");
    }
}
