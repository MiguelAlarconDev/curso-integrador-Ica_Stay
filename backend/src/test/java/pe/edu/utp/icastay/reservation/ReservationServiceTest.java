package pe.edu.utp.icastay.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.hotel.HotelService;
import pe.edu.utp.icastay.hotel.HotelView;
import pe.edu.utp.icastay.room.RoomEntity;

class ReservationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T15:00:00Z");
    private static final UUID GUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROOM_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID HOTEL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 10, 13);

    private ReservationRepository reservations;
    private ReservationUserRepository users;
    private ReservationRoomRepository rooms;
    private HotelService hotels;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        users = mock(ReservationUserRepository.class);
        rooms = mock(ReservationRoomRepository.class);
        hotels = mock(HotelService.class);
        service = new ReservationService(reservations, users, rooms, hotels,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void creaReservaPendingPaymentYCalculaTotalSegunNochesPorPrecio() {
        RoomEntity room = activeRoom(2, new BigDecimal("150.00"));
        when(users.existsByIdAndStatus(GUEST_ID, "ACTIVE")).thenReturn(true);
        when(rooms.findByIdAndStatus(ROOM_ID, "ACTIVE")).thenReturn(Optional.of(room));
        when(hotels.findActive(HOTEL_ID)).thenReturn(Optional.of(
                new HotelView(HOTEL_ID, "Hotel Ica", "Centro", "Av. Principal", "Ica")));
        when(reservations.findOverlapping(ROOM_ID, CHECK_IN, CHECK_OUT)).thenReturn(List.of());
        when(reservations.save(any(ReservationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationView response = service.create(GUEST_ID, validRequest());

        ArgumentCaptor<ReservationEntity> reservationCaptor = ArgumentCaptor.forClass(ReservationEntity.class);
        verify(reservations).save(reservationCaptor.capture());
        ReservationEntity saved = reservationCaptor.getValue();
        assertEquals(GUEST_ID, saved.getGuestUserId());
        assertEquals(ROOM_ID, saved.getRoomId());
        assertEquals(CHECK_IN, saved.getCheckIn());
        assertEquals(CHECK_OUT, saved.getCheckOut());
        assertEquals(2, saved.getGuests());
        assertEquals("PENDING_PAYMENT", saved.getStatus());
        assertEquals(NOW.plusSeconds(900), saved.getExpiresAt());
        assertEquals(new BigDecimal("150.00"), saved.getNightlyPriceSnapshot());
        assertEquals(new BigDecimal("450.00"), saved.getTotalAmount());
        assertEquals("PEN", saved.getCurrency());

        assertEquals("PENDING_PAYMENT", response.status());
        assertEquals(new BigDecimal("450.00"), response.totalAmount());
    }

    @Test
    void usuarioInexistenteOInactivoDevuelve404() {
        when(users.existsByIdAndStatus(GUEST_ID, "ACTIVE")).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(GUEST_ID, validRequest()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(rooms, hotels, reservations);
    }

    @Test
    void habitacionInexistenteOInactivaDevuelve404() {
        when(users.existsByIdAndStatus(GUEST_ID, "ACTIVE")).thenReturn(true);
        when(rooms.findByIdAndStatus(ROOM_ID, "ACTIVE")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(GUEST_ID, validRequest()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(hotels, reservations);
    }

    @Test
    void hotelInactivoDevuelve404() {
        RoomEntity room = activeRoom(2, BigDecimal.TEN);
        when(users.existsByIdAndStatus(GUEST_ID, "ACTIVE")).thenReturn(true);
        when(rooms.findByIdAndStatus(ROOM_ID, "ACTIVE")).thenReturn(Optional.of(room));
        when(hotels.findActive(HOTEL_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(GUEST_ID, validRequest()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(reservations);
    }

    @Test
    void checkOutIgualOAnteriorACheckInDevuelve400() {
        CreateReservationRequest request = new CreateReservationRequest(
                ROOM_ID, CHECK_IN, CHECK_IN, 2);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(GUEST_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(users, rooms, hotels, reservations);
    }

    @Test
    void guestsMenorOIgualACeroDevuelve400() {
        CreateReservationRequest request = new CreateReservationRequest(
                ROOM_ID, CHECK_IN, CHECK_OUT, 0);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(GUEST_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(users, rooms, hotels, reservations);
    }

    @Test
    void guestsSuperiorACapacidadDevuelve400() {
        RoomEntity room = activeRoom(1, BigDecimal.TEN);
        when(users.existsByIdAndStatus(GUEST_ID, "ACTIVE")).thenReturn(true);
        when(rooms.findByIdAndStatus(ROOM_ID, "ACTIVE")).thenReturn(Optional.of(room));
        when(hotels.findActive(HOTEL_ID)).thenReturn(Optional.of(
                new HotelView(HOTEL_ID, "Hotel Ica", "Centro", "Av. Principal", "Ica")));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(GUEST_ID, validRequest()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(reservations);
    }

    @Test
    void conflictoConReservaConfirmedDevuelve409() {
        ReservationEntity blockingReservation = overlappingReservation("CONFIRMED", null);

        assertConflictWhenRepositoryReportsBlockingOverlap(blockingReservation);
    }

    @Test
    void conflictoConPendingPaymentVigenteDevuelve409() {
        ReservationEntity blockingReservation = overlappingReservation("PENDING_PAYMENT", NOW.plusSeconds(1));

        assertConflictWhenRepositoryReportsBlockingOverlap(blockingReservation);
    }

    @Test
    void cancelledExpiredYPendingPaymentVencidaNoBloqueanCuandoNoHaySolapamientoBloqueante() {
        RoomEntity room = activeRoom(2, new BigDecimal("100.00"));
        ReservationEntity cancelled = overlappingReservation("CANCELLED", null);
        ReservationEntity expired = overlappingReservation("EXPIRED", NOW.plusSeconds(3600));
        ReservationEntity expiredPendingPayment = overlappingReservation("PENDING_PAYMENT", NOW.minusSeconds(1));
        when(users.existsByIdAndStatus(GUEST_ID, "ACTIVE")).thenReturn(true);
        when(rooms.findByIdAndStatus(ROOM_ID, "ACTIVE")).thenReturn(Optional.of(room));
        when(hotels.findActive(HOTEL_ID)).thenReturn(Optional.of(
                new HotelView(HOTEL_ID, "Hotel Ica", "Centro", "Av. Principal", "Ica")));
        when(reservations.findOverlapping(ROOM_ID, CHECK_IN, CHECK_OUT))
                .thenReturn(List.of(cancelled, expired, expiredPendingPayment));
        when(reservations.save(any(ReservationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationView response = service.create(GUEST_ID, validRequest());

        assertEquals("PENDING_PAYMENT", response.status());
        assertEquals(new BigDecimal("300.00"), response.totalAmount());
    }

    private void assertConflictWhenRepositoryReportsBlockingOverlap(ReservationEntity blockingReservation) {
        RoomEntity room = activeRoom(2, BigDecimal.TEN);
        when(users.existsByIdAndStatus(GUEST_ID, "ACTIVE")).thenReturn(true);
        when(rooms.findByIdAndStatus(ROOM_ID, "ACTIVE")).thenReturn(Optional.of(room));
        when(hotels.findActive(HOTEL_ID)).thenReturn(Optional.of(
                new HotelView(HOTEL_ID, "Hotel Ica", "Centro", "Av. Principal", "Ica")));
        when(reservations.findOverlapping(ROOM_ID, CHECK_IN, CHECK_OUT))
                .thenReturn(List.of(blockingReservation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create(GUEST_ID, validRequest()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private CreateReservationRequest validRequest() {
        return new CreateReservationRequest(ROOM_ID, CHECK_IN, CHECK_OUT, 2);
    }

    private RoomEntity activeRoom(int capacity, BigDecimal pricePerNight) {
        RoomEntity room = mock(RoomEntity.class);
        when(room.getHotelId()).thenReturn(HOTEL_ID);
        when(room.getCapacity()).thenReturn(capacity);
        when(room.getPricePerNight()).thenReturn(pricePerNight);
        when(room.getCurrency()).thenReturn("PEN");
        return room;
    }

    private ReservationEntity overlappingReservation(String status, Instant expiresAt) {
        ReservationEntity reservation = mock(ReservationEntity.class);
        when(reservation.getStatus()).thenReturn(status);
        when(reservation.getExpiresAt()).thenReturn(expiresAt);
        return reservation;
    }
}
