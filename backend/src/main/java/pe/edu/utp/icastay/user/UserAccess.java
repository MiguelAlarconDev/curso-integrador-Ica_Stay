package pe.edu.utp.icastay.user;
import java.util.UUID;
/** Internal authentication projection; never returned by administrative endpoints. */
public record UserAccess(UUID id, String email, String role, String status, long tokenVersion) {}
