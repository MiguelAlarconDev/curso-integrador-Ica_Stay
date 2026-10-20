package pe.edu.utp.icastay.user;
import java.util.UUID;
public record SuperadminUserDetail(UUID id, String name, String email, String role, String status, Hotel hotel) {
    public record Hotel(UUID id, String name, String status) {}
    public SuperadminUserDetail(UUID id, String name, String email, String role, String status,
            UUID hotelId, String hotelName, String hotelStatus) {
        this(id, name, email, role, status, hotelId == null ? null : new Hotel(hotelId, hotelName, hotelStatus));
    }
}
