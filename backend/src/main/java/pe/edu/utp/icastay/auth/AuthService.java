package pe.edu.utp.icastay.auth;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.user.UserEntity;

@Service
public class AuthService {
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final int MAX_NAME_LENGTH = 160;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request == null) {
            throw invalidRegistration();
        }
        String name = normalizeName(request.name());
        String email = normalizeRegisterEmail(request.email());
        String password = validatePassword(request.password());
        if (users.existsByEmail(email)) {
            throw emailAlreadyRegistered();
        }

        UserEntity user = UserEntity.registeredGuest(
                name,
                email,
                passwordEncoder.encode(password));
        try {
            UserEntity saved = users.saveAndFlush(user);
            return new RegisterResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole());
        } catch (DataIntegrityViolationException exception) {
            throw emailAlreadyRegistered();
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw invalidCredentials();
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Only called by hotel creation inside its transaction; public registration is unchanged.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public UserEntity createHotelAdmin(String name, String email, String password) {
        name = normalizeName(name);
        email = normalizeRegisterEmail(email);
        password = validatePassword(password);
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw invalidRegistration();
        }
        if (users.existsByEmail(email)) throw emailAlreadyRegistered();
        try {
            return users.saveAndFlush(UserEntity.hotelAdministrator(name, email, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && "uq_users_email".equals(constraint.getConstraintName())) throw emailAlreadyRegistered();
            }
            throw exception;
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw invalidRegistration();
        }
        String normalized = name.trim();
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw invalidRegistration();
        }
        return normalized;
    }

    private String normalizeRegisterEmail(String email) {
        if (email == null || email.isBlank()) {
            throw invalidRegistration();
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw invalidRegistration();
        }
        return normalized;
    }

    private String validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw invalidRegistration();
        }
        return password;
    }

    private ResponseStatusException invalidRegistration() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "datos de registro invalidos");
    }

    private ResponseStatusException emailAlreadyRegistered() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "correo ya registrado");
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "credenciales invalidas");
    }
}
