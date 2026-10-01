package pe.edu.utp.icastay.auth;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, String role) {
}
