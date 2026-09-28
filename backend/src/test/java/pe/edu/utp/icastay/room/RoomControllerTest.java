package pe.edu.utp.icastay.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @Test
    void devuelveHabitacionesCuandoElHotelActivoTieneHabitacionesVisibles() {
        UUID hotelId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<RoomView> expected = List.of(new RoomView(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "101", "Habitacion doble", 2, new BigDecimal("180.00"), "PEN"));
        when(roomService.listVisibleByActiveHotel(hotelId)).thenReturn(Optional.of(expected));

        List<RoomView> response = new RoomController(roomService).listByHotel(hotelId);

        assertSame(expected, response);
        verify(roomService).listVisibleByActiveHotel(hotelId);
    }

    @Test
    void devuelveListaVaciaCuandoElHotelActivoNoTieneHabitacionesVisibles() {
        UUID hotelId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        List<RoomView> expected = List.of();
        when(roomService.listVisibleByActiveHotel(hotelId)).thenReturn(Optional.of(expected));

        List<RoomView> response = new RoomController(roomService).listByHotel(hotelId);

        assertSame(expected, response);
        verify(roomService).listVisibleByActiveHotel(hotelId);
    }

    @Test
    void devuelve404CuandoElHotelNoExisteONoEstaActivo() {
        UUID hotelId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(roomService.listVisibleByActiveHotel(hotelId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new RoomController(roomService).listByHotel(hotelId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(roomService).listVisibleByActiveHotel(hotelId);
    }
}
