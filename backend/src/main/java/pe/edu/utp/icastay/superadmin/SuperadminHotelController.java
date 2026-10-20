package pe.edu.utp.icastay.superadmin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.UUID;
import pe.edu.utp.icastay.hotel.SuperadminHotelDetail;
import pe.edu.utp.icastay.hotel.UpdateHotelRequest;
import pe.edu.utp.icastay.hotel.HotelStatusRequest;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import pe.edu.utp.icastay.hotel.CreateHotelRequest;
import pe.edu.utp.icastay.hotel.CreateHotelService;
import pe.edu.utp.icastay.hotel.SuperadminHotelView;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.icastay.hotel.HotelService;
import pe.edu.utp.icastay.hotel.SuperadminHotelPage;

@RestController
@RequestMapping("/api/v1/superadmin/hotels")
public class SuperadminHotelController {
    private final HotelService hotels;
    private final CreateHotelService creation;
    private final ObjectMapper mapper;

    public SuperadminHotelController(HotelService hotels, CreateHotelService creation, ObjectMapper mapper) {
        this.hotels = hotels;
        this.creation = creation;
        this.mapper = mapper;
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<SuperadminHotelView> create(HttpServletRequest request) {
        CreateHotelRequest body;
        // Read with the configured mapper, but never pass parser errors containing
        // password fragments to MVC's argument-resolution/error DEBUG loggers.
        try {
            body = mapper.readValue(request.getInputStream(), CreateHotelRequest.class);
        } catch (JacksonException | IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datos de hotel y administrador invalidos");
        }
        return ResponseEntity.status(201).body(creation.create(body));
    }

    @GetMapping
    public SuperadminHotelPage list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String name, @RequestParam(required = false) String status) {
        return hotels.listGlobal(page, name, status);
    }

    @GetMapping("/{id}")
    public SuperadminHotelDetail detail(@PathVariable UUID id) { return hotels.findGlobal(id); }

    @PutMapping("/{id}")
    public SuperadminHotelDetail update(@PathVariable UUID id, @RequestBody UpdateHotelRequest request) {
        return hotels.updateGlobal(id, request);
    }

    @PatchMapping("/{id}/status")
    public SuperadminHotelDetail status(@PathVariable UUID id, @RequestBody HotelStatusRequest request) {
        return hotels.changeGlobalStatus(id, request);
    }
}
