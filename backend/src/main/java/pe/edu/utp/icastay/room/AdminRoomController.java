package pe.edu.utp.icastay.room;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.icastay.auth.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/admin/hotel/rooms")
public class AdminRoomController {
    private final RoomService rooms;

    public AdminRoomController(RoomService rooms) { this.rooms = rooms; }

    @GetMapping
    public List<AdminRoomView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return rooms.listAssigned(user.id());
    }

    @GetMapping("/{id}")
    public AdminRoomView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return rooms.findAssigned(user.id(), id);
    }

    @PostMapping
    public ResponseEntity<AdminRoomView> create(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody SaveRoomRequest request) {
        AdminRoomView room = rooms.createAssigned(user.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/hotel/rooms/" + room.id())).body(room);
    }

    @PutMapping("/{id}")
    public AdminRoomView update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @RequestBody SaveRoomRequest request) {
        return rooms.updateAssigned(user.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public AdminRoomView status(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @RequestBody RoomStatusRequest request) {
        return rooms.changeAssignedStatus(user.id(), id, request);
    }
}
