package pe.edu.utp.icastay.hotel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HotelService {
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final HotelRepository hotels;

    public HotelService(HotelRepository hotels) {
        this.hotels = hotels;
    }

    @Transactional(readOnly = true)
    public SuperadminHotelDetail findGlobal(UUID id) {
        return hotels.findGlobalDetail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public SuperadminHotelDetail updateGlobal(UUID id, UpdateHotelRequest request) {
        HotelEntity hotel = hotels.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var details = validateDetails(request);
        hotel.updateDetails(details.name(), details.description(), details.address(), details.city());
        hotels.flush();
        return findGlobal(id);
    }

    @Transactional
    public SuperadminHotelDetail changeGlobalStatus(UUID id, HotelStatusRequest request) {
        HotelEntity hotel = hotels.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request == null || !("ACTIVE".equals(request.status()) || "INACTIVE".equals(request.status()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado invalido");
        }
        hotel.changeStatus(request.status());
        hotels.flush();
        return findGlobal(id);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<HotelView> findActiveForBooking(UUID id) {
        return hotels.findActiveForBooking(id).map(this::toView);
    }

    @Transactional(readOnly = true)
    public SuperadminHotelPage listGlobal(int page, String name, String status) {
        final int size = 20;
        if (page < 0 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paginacion invalida");
        }
        if (status != null && !status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado invalido");
        }
        var result = hotels.searchGlobal(searchText(name, 180), status, PageRequest.of(page, size));
        return new SuperadminHotelPage(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public HotelPage listActive(int page, int size) {
        return listActive(page, size, null, null);
    }

    public HotelPage listActive(int page, int size, String name, String city) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paginacion invalida");
        }
        name = searchText(name, 180);
        city = searchText(city, 120);
        var pageable = PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id")));
        Page<HotelEntity> result = name.isEmpty() && city.isEmpty()
                ? hotels.findByStatus(ACTIVE_STATUS, pageable) : hotels.searchActive(name, city, pageable);
        List<HotelView> content = result.getContent().stream()
                .map(this::toView)
                .toList();
        return new HotelPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private String searchText(String value, int max) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filtro demasiado largo");
        return normalized;
    }

    public Optional<HotelView> findActive(UUID id) {
        return hotels.findByIdAndStatus(id, ACTIVE_STATUS)
                .map(this::toView);
    }

    @Transactional(readOnly = true)
    public HotelView findAssigned(UUID adminUserId) {
        return toView(assignedHotel(adminUserId));
    }

    @Transactional
    public HotelView updateAssigned(UUID adminUserId, UpdateHotelRequest request) {
        HotelEntity hotel = hotels.findAssignedForUpdate(adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        UpdateHotelRequest details = validateDetails(request);
        hotel.updateDetails(details.name(), details.description(), details.address(), details.city());
        return toView(hotel);
    }

    static UpdateHotelRequest validateDetails(UpdateHotelRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datos de hotel invalidos");
        }
        String name = requiredText(request.name(), 180);
        String address = requiredText(request.address(), 255);
        String city = requiredText(request.city(), 120);
        String description = request.description() == null ? null : request.description().strip();
        if (description != null && description.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "descripcion demasiado larga");
        }
        if (description != null && description.isBlank()) description = null;
        return new UpdateHotelRequest(name, description, address, city);
    }

    private HotelEntity assignedHotel(UUID adminUserId) {
        return hotels.findByAdminUserId(adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static String requiredText(String value, int maxLength) {
        if (value == null || value.isBlank() || value.strip().length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datos de hotel invalidos");
        }
        return value.strip();
    }

    private HotelView toView(HotelEntity hotel) {
        return new HotelView(hotel.getId(), hotel.getName(), hotel.getDescription(),
                hotel.getAddress(), hotel.getCity());
    }
}
