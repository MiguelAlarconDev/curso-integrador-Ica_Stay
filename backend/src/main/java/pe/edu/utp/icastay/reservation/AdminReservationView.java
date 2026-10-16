package pe.edu.utp.icastay.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminReservationView(UUID id, LocalDate checkIn, LocalDate checkOut, int guests,
        String status, Instant expiresAt, BigDecimal nightlyPriceSnapshot, BigDecimal totalAmount,
        String currency, Instant createdAt, UUID roomId, String roomNumber,
        UUID guestUserId, String guestName, String guestEmail) {
}
