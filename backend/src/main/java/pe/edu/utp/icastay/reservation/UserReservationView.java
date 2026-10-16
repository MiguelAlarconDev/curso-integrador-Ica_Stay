package pe.edu.utp.icastay.reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserReservationView(UUID id, UUID hotelId, String hotelName, UUID roomId,
        String roomNumber, LocalDate checkIn, LocalDate checkOut, int guests, String status,
        Instant expiresAt, BigDecimal nightlyPriceSnapshot, BigDecimal totalAmount, String currency,
        Instant createdAt, Instant evaluatedAt, boolean holdExpired) {
    public UserReservationView(UUID id, UUID hotelId, String hotelName, UUID roomId,
            String roomNumber, LocalDate checkIn, LocalDate checkOut, int guests, String status,
            Instant expiresAt, BigDecimal nightlyPriceSnapshot, BigDecimal totalAmount, String currency,
            Instant createdAt) {
        this(id, hotelId, hotelName, roomId, roomNumber, checkIn, checkOut, guests, status,
                expiresAt, nightlyPriceSnapshot, totalAmount, currency, createdAt, null, false);
    }

    UserReservationView evaluatedAt(Instant now) {
        return new UserReservationView(id, hotelId, hotelName, roomId, roomNumber, checkIn, checkOut,
                guests, status, expiresAt, nightlyPriceSnapshot, totalAmount, currency, createdAt, now,
                "PENDING_PAYMENT".equals(status) && expiresAt != null && !expiresAt.isAfter(now));
    }
}
