package pe.edu.utp.icastay.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rooms")
public class RoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hotel_id", nullable = false)
    private UUID hotelId;

    @Column(nullable = false, length = 40)
    private String number;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "price_per_night", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    protected RoomEntity() {
    }

    public UUID getId() { return id; }
    public UUID getHotelId() { return hotelId; }
    public String getNumber() { return number; }
    public String getDescription() { return description; }
    public int getCapacity() { return capacity; }
    public BigDecimal getPricePerNight() { return pricePerNight; }
    public String getCurrency() { return currency; }
}
