package pe.edu.utp.icastay.room;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminRoomView(UUID id, String number, String description, int capacity,
        BigDecimal pricePerNight, String currency, String status) {
}
