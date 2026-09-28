package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
class HotelControllerTest {

    @Mock
    private HotelService hotelService;

    @Test
    void delegaElListadoCuandoLosParametrosSonValidos() {
        HotelPage expected = new HotelPage(List.of(), 0, 20, 0, 0);
        when(hotelService.listActive(0, 20)).thenReturn(expected);

        HotelPage response = new HotelController(hotelService).list(0, 20);

        assertSame(expected, response);
        verify(hotelService).listActive(0, 20);
    }

    @Test
    void rechazaParametrosInvalidosAntesDeDelegar() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new HotelController(hotelService).list(-1, 20));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(hotelService);
    }

    @Test
    void devuelveHotelActivoPorId() {
        UUID hotelId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        HotelView expected = new HotelView(hotelId, "Hotel Oasis", "Vista al desierto",
                "Calle Palmeras 456", "Ica");
        when(hotelService.findActive(hotelId)).thenReturn(Optional.of(expected));

        HotelView response = new HotelController(hotelService).getById(hotelId);

        assertSame(expected, response);
        verify(hotelService).findActive(hotelId);
    }

    @Test
    void devuelve404CuandoElHotelNoExisteONoEstaActivo() {
        UUID hotelId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(hotelService.findActive(hotelId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new HotelController(hotelService).getById(hotelId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(hotelService).findActive(hotelId);
    }
}
