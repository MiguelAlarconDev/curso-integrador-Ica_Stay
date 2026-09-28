package pe.edu.utp.icastay.hotel;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class HotelService {
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final HotelRepository hotels;

    public HotelService(HotelRepository hotels) {
        this.hotels = hotels;
    }

    public HotelPage listActive(int page, int size) {
        Page<HotelEntity> result = hotels.findByStatus(ACTIVE_STATUS,
                PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))));
        List<HotelView> content = result.getContent().stream()
                .map(this::toView)
                .toList();
        return new HotelPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private HotelView toView(HotelEntity hotel) {
        return new HotelView(hotel.getId(), hotel.getName(), hotel.getDescription(),
                hotel.getAddress(), hotel.getCity());
    }
}
