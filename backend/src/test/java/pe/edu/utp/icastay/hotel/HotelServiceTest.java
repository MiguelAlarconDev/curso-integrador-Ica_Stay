package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotels;

    @Test
    void listaHotelesActivosConPaginacionOrdenYMapeoExistentes() {
        UUID hotelId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        HotelEntity hotel = mock(HotelEntity.class);
        when(hotel.getId()).thenReturn(hotelId);
        when(hotel.getName()).thenReturn("Hotel Ica");
        when(hotel.getDescription()).thenReturn("Cerca de la plaza");
        when(hotel.getAddress()).thenReturn("Av. Principal 123");
        when(hotel.getCity()).thenReturn("Ica");

        when(hotels.findByStatus(eq("ACTIVE"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(hotel), Pageable.ofSize(20).withPage(0), 1));

        HotelPage response = new HotelService(hotels).listActive(0, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(hotels).findByStatus(eq("ACTIVE"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("name").getDirection());
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("id").getDirection());

        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(1, response.totalElements());
        assertEquals(1, response.totalPages());
        assertEquals(List.of(new HotelView(hotelId, "Hotel Ica", "Cerca de la plaza",
                "Av. Principal 123", "Ica")), response.content());
    }
}
