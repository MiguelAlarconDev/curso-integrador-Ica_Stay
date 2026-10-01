package pe.edu.utp.icastay.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import pe.edu.utp.icastay.auth.AuthenticatedUser;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock
    private ReservationService reservationService;

    @Test
    void delegaCreacionYDevuelveDtoDelService() {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "user@example.com",
                "USER");
        CreateReservationRequest request = new CreateReservationRequest(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 13),
                2);
        ReservationView expected = new ReservationView(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                user.id(),
                request.roomId(),
                request.checkIn(),
                request.checkOut(),
                request.guests(),
                "PENDING_PAYMENT",
                Instant.parse("2026-09-30T15:15:00Z"),
                new BigDecimal("150.00"),
                new BigDecimal("450.00"),
                "PEN");
        when(reservationService.create(user.id(), request)).thenReturn(expected);

        ReservationView response = new ReservationController(reservationService).create(user, request);

        assertSame(expected, response);
        verify(reservationService).create(user.id(), request);
    }

    @Test
    void endpointCreateDeclaraHttp201() throws NoSuchMethodException {
        Method method = ReservationController.class.getMethod("create",
                AuthenticatedUser.class, CreateReservationRequest.class);
        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

        assertEquals(HttpStatus.CREATED, responseStatus.value());
    }

    @Test
    void createReservationRequestNoAceptaGuestUserId() {
        boolean hasGuestUserId = Arrays.stream(CreateReservationRequest.class.getRecordComponents())
                .anyMatch(component -> "guestUserId".equals(component.getName()));

        assertFalse(hasGuestUserId);
    }
}
