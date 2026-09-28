package pe.edu.utp.icastay.room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utp.icastay.hotel.HotelService;
import pe.edu.utp.icastay.hotel.HotelView;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository rooms;

    @Mock
    private HotelService hotels;

    @Test
    void listaHabitacionesVisiblesCuandoElHotelEstaActivo() {
        UUID hotelId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID roomId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RoomEntity room = mock(RoomEntity.class);
        when(room.getId()).thenReturn(roomId);
        when(room.getNumber()).thenReturn("101");
        when(room.getDescription()).thenReturn("Habitacion doble");
        when(room.getCapacity()).thenReturn(2);
        when(room.getPricePerNight()).thenReturn(new BigDecimal("180.00"));
        when(room.getCurrency()).thenReturn("PEN");
        when(hotels.findActive(hotelId)).thenReturn(Optional.of(
                new HotelView(hotelId, "Hotel Ica", "Centro", "Av. Principal", "Ica")));
        when(rooms.findByHotelIdAndStatusOrderByNumberAscIdAsc(hotelId, "ACTIVE"))
                .thenReturn(List.of(room));

        Optional<List<RoomView>> response = new RoomService(rooms, hotels).listVisibleByActiveHotel(hotelId);

        assertEquals(Optional.of(List.of(new RoomView(roomId, "101", "Habitacion doble",
                2, new BigDecimal("180.00"), "PEN"))), response);
        verify(rooms).findByHotelIdAndStatusOrderByNumberAscIdAsc(hotelId, "ACTIVE");
    }

    @Test
    void devuelveListaVaciaCuandoElHotelActivoNoTieneHabitacionesVisibles() {
        UUID hotelId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(hotels.findActive(hotelId)).thenReturn(Optional.of(
                new HotelView(hotelId, "Hotel Ica", "Centro", "Av. Principal", "Ica")));
        when(rooms.findByHotelIdAndStatusOrderByNumberAscIdAsc(hotelId, "ACTIVE"))
                .thenReturn(List.of());

        Optional<List<RoomView>> response = new RoomService(rooms, hotels).listVisibleByActiveHotel(hotelId);

        assertEquals(Optional.of(List.of()), response);
        verify(rooms).findByHotelIdAndStatusOrderByNumberAscIdAsc(hotelId, "ACTIVE");
    }

    @Test
    void noConsultaHabitacionesCuandoElHotelNoExisteONoEstaActivo() {
        UUID hotelId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(hotels.findActive(hotelId)).thenReturn(Optional.empty());

        Optional<List<RoomView>> response = new RoomService(rooms, hotels).listVisibleByActiveHotel(hotelId);

        assertTrue(response.isEmpty());
        verifyNoInteractions(rooms);
    }
}
