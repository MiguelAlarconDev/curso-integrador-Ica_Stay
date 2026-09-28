package pe.edu.utp.icastay.hotel;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hotels")
public class HotelController {
    private final HotelRepository hotels;

    public HotelController(HotelRepository hotels) {
        this.hotels = hotels;
    }

    @GetMapping
    public HotelPage list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "page debe ser >= 0 y size debe estar entre 1 y 100");
        }
        Page<HotelEntity> result = hotels.findByStatus(
                "ACTIVE", PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))));
        List<HotelView> content = result.getContent().stream()
                .map(h -> new HotelView(h.getId(), h.getName(), h.getDescription(), h.getAddress(), h.getCity()))
                .toList();
        return new HotelPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public record HotelView(UUID id, String name, String description, String address, String city) {
    }

    public record HotelPage(List<HotelView> content, int page, int size, long totalElements, int totalPages) {
    }
}
