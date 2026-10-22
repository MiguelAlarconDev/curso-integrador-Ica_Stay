package pe.edu.utp.icastay.ai.history;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/** Derives a reference from a future server-generated, random 256-bit session secret. */
public final class AnonymousOwnerReference {
    private AnonymousOwnerReference() {}

    public static String derive(String sessionSecret) {
        if (sessionSecret == null || !sessionSecret.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        byte[] decoded = Base64.getUrlDecoder().decode(sessionSecret);
        if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(sessionSecret)) {
            throw invalid();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("icastay-ai-owner:v1:".getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(decoded));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible");
        } finally {
            java.util.Arrays.fill(decoded, (byte) 0);
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Identidad anónima inválida");
    }
}
