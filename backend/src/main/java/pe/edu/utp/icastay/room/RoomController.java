package pe.edu.utp.icastay.room;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/hotels/{hotelId}/rooms")
public class RoomController {
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomView> search(@PathVariable UUID hotelId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String checkIn,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String checkOut,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String guests) {
        if (checkIn == null && checkOut == null && guests == null) return listByHotel(hotelId);
        Integer count = null;
        if (guests != null) {
            try { count = Integer.valueOf(guests); }
            catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
        }
        return roomService.available(hotelId, checkIn, checkOut, count)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<RoomView> listByHotel(@PathVariable UUID hotelId) {
        return roomService.listVisibleByActiveHotel(hotelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
