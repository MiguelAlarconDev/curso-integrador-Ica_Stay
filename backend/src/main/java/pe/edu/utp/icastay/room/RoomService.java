package pe.edu.utp.icastay.room;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private RoomView toView(RoomEntity room) {
        return new RoomView(room.getId(), room.getNumber(), room.getDescription(),
                room.getCapacity(), room.getPricePerNight(), room.getCurrency());
    }
}
