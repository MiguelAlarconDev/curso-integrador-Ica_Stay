package pe.edu.utp.icastay.ai;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import pe.edu.utp.icastay.ai.history.AnonymousOwnerReference;

@Service
public class AiSessionService {
    public static final String COOKIE_NAME = "icastay_ai_session";
    private static final Duration LIFETIME = Duration.ofDays(30);
    private final byte[] key;
    private final boolean secure;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public AiSessionService(@Value("${icastay.ai.session.signing-key:}") String key,
            @Value("${icastay.ai.session.secure:true}") boolean secure,
            @Value("${icastay.cors.allowed-origin}") String origin) {
        this(key, secure, origin, Clock.systemUTC());
    }

    AiSessionService(String configuredKey, boolean secure, String origin, Clock clock) {
        boolean local = origin.matches("http://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?");
        if (!secure && !local) throw new IllegalArgumentException("Cookie insegura solo permitida en desarrollo local HTTP");
        if ((configuredKey == null || configuredKey.isBlank()) && !local) {
            throw new IllegalArgumentException("Configura la clave de firma de sesión IA");
        }
        if (configuredKey == null || configuredKey.isBlank()) {
            this.key = new byte[32]; random.nextBytes(this.key);
        } else {
            try { this.key = Base64.getUrlDecoder().decode(configuredKey); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Clave de sesión IA inválida"); }
            if (this.key.length < 32) throw new IllegalArgumentException("Clave de sesión IA demasiado corta");
        }
        this.secure = secure;
        this.clock = clock;
    }

    public record Session(String secret, String conversationId, Instant expiresAt) {
        @Override public String toString() { return "AiSession[omitida]"; }
    }

    public Session create() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return new Session(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                UUID.randomUUID().toString(), clock.instant().plus(LIFETIME));
    }

    public Optional<Session> read(HttpServletRequest request) {
        String value = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                if (value != null) return Optional.empty();
                value = cookie.getValue();
            }
        }
        return decode(value);
    }

    Optional<Session> decode(String value) {
        if (value == null || value.length() > 220) return Optional.empty();
        try {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 5 || !parts[0].equals("1")) return Optional.empty();
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(sign(payload), Base64.getUrlDecoder().decode(parts[4]))) return Optional.empty();
            AnonymousOwnerReference.derive(parts[1]);
            UUID id = UUID.fromString(parts[2]);
            if (id.version() != 4 || id.variant() != 2 || !id.toString().equals(parts[2])) return Optional.empty();
            Instant expires = Instant.ofEpochSecond(Long.parseLong(parts[3]));
            if (!expires.isAfter(clock.instant()) || expires.isAfter(clock.instant().plus(LIFETIME))) return Optional.empty();
            return Optional.of(new Session(parts[1], parts[2], expires));
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) { return Optional.empty(); }
    }

    public String cookie(Session session) {
        String payload = "1." + session.secret() + "." + session.conversationId() + "." + clock.instant().plus(LIFETIME).getEpochSecond();
        return baseCookie(payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload)))
                .maxAge(LIFETIME).build().toString();
    }

    public String clearCookie() { return baseCookie("").maxAge(Duration.ZERO).build().toString(); }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secure).sameSite("Lax").path("/api/v1/ai");
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(("icastay-ai-session:v1:" + payload).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException impossible) { throw new IllegalStateException("Firma IA no disponible"); }
    }
}
