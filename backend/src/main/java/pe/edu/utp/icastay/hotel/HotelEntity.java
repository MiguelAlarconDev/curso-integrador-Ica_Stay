package pe.edu.utp.icastay.hotel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "hotels")
public class HotelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_user_id", nullable = false, unique = true)
    private UUID adminUserId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 20)
    private String status;

    protected HotelEntity() {
    }

    static HotelEntity create(UUID adminUserId, UpdateHotelRequest details) {
        HotelEntity hotel = new HotelEntity();
        hotel.adminUserId = adminUserId;
        hotel.status = "ACTIVE";
        hotel.updateDetails(details.name(), details.description(), details.address(), details.city());
        return hotel;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public String getCity() { return city; }

    void changeStatus(String status) { this.status = status; }

    void updateDetails(String name, String description, String address, String city) {
        this.name = name;
        this.description = description;
        this.address = address;
        this.city = city;
    }
}
