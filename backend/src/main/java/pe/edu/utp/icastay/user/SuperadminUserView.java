package pe.edu.utp.icastay.user;
import java.util.UUID;
public record SuperadminUserView(UUID id, String name, String email, String role, String status) {}
