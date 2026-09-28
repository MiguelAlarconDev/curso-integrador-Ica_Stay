package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
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
}
