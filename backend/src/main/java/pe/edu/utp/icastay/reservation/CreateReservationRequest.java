package pe.edu.utp.icastay.reservation;

import java.time.LocalDate;
import java.util.UUID;

public record CreateReservationRequest(
        UUID guestUserId,
        UUID roomId,
        LocalDate checkIn,
        LocalDate checkOut,
        int guests) {
}
