package pe.edu.utp.icastay.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationView(
        UUID id,
        UUID guestUserId,
        UUID roomId,
        LocalDate checkIn,
        LocalDate checkOut,
        int guests,
        String status,
        Instant expiresAt,
        BigDecimal nightlyPriceSnapshot,
        BigDecimal totalAmount,
        String currency) {
}
