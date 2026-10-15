package pe.edu.utp.icastay.auth;

import java.util.UUID;

public record RegisterResponse(UUID id, String name, String email, String role) {
}
