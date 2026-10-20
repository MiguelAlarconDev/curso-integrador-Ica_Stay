package pe.edu.utp.icastay.auth;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, String role, long tokenVersion) {
    public AuthenticatedUser(UUID id, String email, String role) { this(id, email, role, 0); }
}
