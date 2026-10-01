package pe.edu.utp.icastay.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.edu.utp.icastay.user.UserEntity;

@Service
public class JwtTokenService {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final long TOKEN_TTL_SECONDS = 3600;

    private final String secret;
    private final Clock clock;

    @Autowired
    public JwtTokenService(@Value("${icastay.security.jwt.secret:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    JwtTokenService(String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    public String createToken(UserEntity user) {
        ensureSecretConfigured();
        Instant now = Instant.now(clock);
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getEmail());
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole());
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond());

        String unsignedToken = encodeJson(header) + "." + encodeJson(claims);
        return unsignedToken + "." + sign(unsignedToken);
    }

    public Map<String, Object> validateAndReadClaims(String token) {
        ensureSecretConfigured();
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("JWT invalido");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            throw new IllegalArgumentException("JWT con firma invalida");
        }
        Map<String, Object> claims = decodeClaims(parts[1]);
        Object exp = claims.get("exp");
        if (!(exp instanceof Number expiration) || Instant.now(clock).getEpochSecond() >= expiration.longValue()) {
            throw new IllegalArgumentException("JWT expirado");
        }
        return claims;
    }

    private String encodeJson(Map<String, Object> value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> decodeClaims(String encodedClaims) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(encodedClaims), StandardCharsets.UTF_8);
            return fromJson(json);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT invalido", exception);
        }
    }

    private String toJson(Map<String, Object> value) {
        return value.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":" + jsonValue(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String jsonValue(Object value) {
        if (value instanceof Number) {
            return value.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private Map<String, Object> fromJson(String json) {
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("JSON invalido");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        String body = json.substring(1, json.length() - 1);
        if (body.isBlank()) {
            return result;
        }
        for (String pair : body.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("JSON invalido");
            }
            String key = unquote(parts[0].trim());
            String rawValue = parts[1].trim();
            Object value = rawValue.startsWith("\"") ? unquote(rawValue) : Long.parseLong(rawValue);
            result.put(key, value);
        }
        return result;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unquote(String value) {
        if (!value.startsWith("\"") || !value.endsWith("\"")) {
            throw new IllegalArgumentException("JSON invalido");
        }
        return value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo firmar JWT", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigestUtil.constantTimeEquals(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private void ensureSecretConfigured() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("icastay.security.jwt.secret debe tener al menos 32 caracteres");
        }
    }
}
