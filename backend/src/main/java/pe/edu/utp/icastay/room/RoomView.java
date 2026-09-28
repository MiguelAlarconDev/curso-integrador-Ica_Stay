package pe.edu.utp.icastay.room;

import java.math.BigDecimal;
import java.util.UUID;

public record RoomView(
        UUID id,
        String number,
        String description,
        int capacity,
        BigDecimal pricePerNight,
        String currency) {
}
