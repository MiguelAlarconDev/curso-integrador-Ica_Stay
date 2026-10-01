package pe.edu.utp.icastay.reservation;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.hotel.HotelService;
import pe.edu.utp.icastay.room.RoomEntity;

@Service
public class ReservationService {
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String CONFIRMED_STATUS = "CONFIRMED";
    private static final String PENDING_PAYMENT_STATUS = "PENDING_PAYMENT";
    private static final long HOLD_MINUTES = 15;

    private final ReservationRepository reservations;
    private final ReservationUserRepository users;
    private final ReservationRoomRepository rooms;
    private final HotelService hotels;
    private final Clock clock;

    @Autowired
    public ReservationService(
            ReservationRepository reservations,
            ReservationUserRepository users,
            ReservationRoomRepository rooms,
            HotelService hotels) {
        this(reservations, users, rooms, hotels, Clock.systemUTC());
    }

    ReservationService(
            ReservationRepository reservations,
            ReservationUserRepository users,
            ReservationRoomRepository rooms,
            HotelService hotels,
            Clock clock) {
        this.reservations = reservations;
        this.users = users;
        this.rooms = rooms;
        this.hotels = hotels;
        this.clock = clock;
    }

    @Transactional
    public ReservationView create(CreateReservationRequest request) {
        if (request.checkOut() == null || request.checkIn() == null
                || !request.checkOut().isAfter(request.checkIn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "checkOut debe ser posterior a checkIn");
        }
        if (request.guests() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guests debe ser mayor que 0");
        }
        if (!users.existsByIdAndStatus(request.guestUserId(), ACTIVE_STATUS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        RoomEntity room = rooms.findByIdAndStatus(request.roomId(), ACTIVE_STATUS)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (hotels.findActive(room.getHotelId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (request.guests() > room.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guests supera la capacidad de la habitacion");
        }

        Instant now = Instant.now(clock);
        if (hasBlockingOverlap(request, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "habitacion no disponible");
        }

        long nights = ChronoUnit.DAYS.between(request.checkIn(), request.checkOut());
        BigDecimal nightlyPriceSnapshot = room.getPricePerNight();
        BigDecimal totalAmount = nightlyPriceSnapshot.multiply(BigDecimal.valueOf(nights));
        ReservationEntity reservation = ReservationEntity.pendingPayment(
                request.guestUserId(),
                request.roomId(),
                request.checkIn(),
                request.checkOut(),
                request.guests(),
                now.plus(HOLD_MINUTES, ChronoUnit.MINUTES),
                nightlyPriceSnapshot,
                totalAmount,
                room.getCurrency());

        return toView(reservations.save(reservation));
    }

    private boolean hasBlockingOverlap(CreateReservationRequest request, Instant now) {
        List<ReservationEntity> overlapping = reservations.findOverlapping(
                request.roomId(), request.checkIn(), request.checkOut());
        return overlapping.stream().anyMatch(reservation ->
                CONFIRMED_STATUS.equals(reservation.getStatus())
                        || (PENDING_PAYMENT_STATUS.equals(reservation.getStatus())
                        && reservation.getExpiresAt() != null
                        && reservation.getExpiresAt().isAfter(now)));
    }

    private ReservationView toView(ReservationEntity reservation) {
        return new ReservationView(
                reservation.getId(),
                reservation.getGuestUserId(),
                reservation.getRoomId(),
                reservation.getCheckIn(),
                reservation.getCheckOut(),
                reservation.getGuests(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getNightlyPriceSnapshot(),
                reservation.getTotalAmount(),
                reservation.getCurrency());
    }
}
