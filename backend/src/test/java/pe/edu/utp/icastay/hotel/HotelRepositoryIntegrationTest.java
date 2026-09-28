package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
class HotelRepositoryIntegrationTest {

    @Autowired
    private HotelRepository hotels;

    @Test
    void consultaHotelesActivosSobreElEsquemaMigrado() {
        assertNotNull(hotels.findByStatus("ACTIVE", PageRequest.of(0, 10)));
    }
}
