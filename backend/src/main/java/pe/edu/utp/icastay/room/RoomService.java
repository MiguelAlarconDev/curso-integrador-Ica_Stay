package pe.edu.utp.icastay.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.Currency;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import pe.edu.utp.icastay.hotel.HotelService;

@Service
public class RoomService {
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final RoomRepository rooms;
    private final HotelService hotels;

    public RoomService(RoomRepository rooms, HotelService hotels) {
        this.rooms = rooms;
        this.hotels = hotels;
    }

    public Optional<List<RoomView>> listVisibleByActiveHotel(UUID hotelId) {
        if (hotels.findActive(hotelId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rooms.findByHotelIdAndStatusOrderByNumberAscIdAsc(hotelId, ACTIVE_STATUS).stream()
                .map(this::toView)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<AdminRoomView> listAssigned(UUID adminId) {
        UUID hotelId = hotels.findAssigned(adminId).id();
        return rooms.findByHotelIdOrderByNumberAscIdAsc(hotelId).stream().map(this::toAdminView).toList();
    }

    @Transactional(readOnly = true)
    public AdminRoomView findAssigned(UUID adminId, UUID roomId) {
        UUID hotelId = hotels.findAssigned(adminId).id();
        return toAdminView(rooms.findByIdAndHotelId(roomId, hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Transactional
    public AdminRoomView createAssigned(UUID adminId, SaveRoomRequest request) {
        RoomEntity room = RoomEntity.create(hotels.findAssigned(adminId).id());
        applyDetails(room, request);
        return saveAssigned(room);
    }

    @Transactional
    public AdminRoomView updateAssigned(UUID adminId, UUID roomId, SaveRoomRequest request) {
        RoomEntity room = ownedForUpdate(adminId, roomId);
        applyDetails(room, request);
        return saveAssigned(room);
    }

    @Transactional
    public AdminRoomView changeAssignedStatus(UUID adminId, UUID roomId, RoomStatusRequest request) {
        RoomEntity room = ownedForUpdate(adminId, roomId);
        if (request == null || !("ACTIVE".equals(request.status()) || "INACTIVE".equals(request.status()))) {
            throw invalid("status debe ser ACTIVE o INACTIVE");
        }
        room.changeStatus(request.status());
        return toAdminView(room);
    }

    private RoomEntity ownedForUpdate(UUID adminId, UUID roomId) {
        UUID hotelId = hotels.findAssigned(adminId).id();
        return rooms.findOwnedForUpdate(roomId, hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void applyDetails(RoomEntity room, SaveRoomRequest request) {
        if (request == null || request.number() == null || request.number().isBlank()
                || request.number().length() > 40 || !request.number().equals(request.number().strip())) {
            throw invalid("number es obligatorio, sin espacios exteriores y de hasta 40 caracteres");
        }
        int capacity;
        try {
            capacity = request.capacity() == null ? 0 : request.capacity().intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid("capacity debe ser un entero positivo");
        }
        if (capacity <= 0) throw invalid("capacity debe ser un entero positivo");
        BigDecimal price = request.pricePerNight();
        if (price == null || price.signum() <= 0 || price.scale() > 2
                || price.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw invalid("pricePerNight debe ser positivo, hasta 9999999999.99 y con maximo dos decimales");
        }
        String currency = request.currency() == null ? "PEN" : request.currency();
        try {
            if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException();
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw invalid("currency debe ser un codigo ISO 4217 de tres letras mayusculas");
        }
        String description = request.description();
        if (description != null) {
            description = description.strip();
            if (description.isEmpty()) description = null;
        }
        room.updateDetails(request.number(), description, capacity, price, currency);
    }

    private AdminRoomView saveAssigned(RoomEntity room) {
        try {
            // Flush inside the transaction: the existing UNIQUE constraint also covers concurrent requests.
            return toAdminView(rooms.saveAndFlush(room));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_rooms_hotel_number".equals(violation.getConstraintName())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "numero de habitacion ya registrado");
                }
            }
            throw exception;
        }
    }

    private ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private AdminRoomView toAdminView(RoomEntity room) {
        return new AdminRoomView(room.getId(), room.getNumber(), room.getDescription(), room.getCapacity(),
                room.getPricePerNight(), room.getCurrency(), room.getStatus());
    }

    private RoomView toView(RoomEntity room) {
        return new RoomView(room.getId(), room.getNumber(), room.getDescription(),
                room.getCapacity(), room.getPricePerNight(), room.getCurrency());
    }
}
