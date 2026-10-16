package pe.edu.utp.icastay.hotel;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.utp.icastay.auth.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/admin/hotel")
public class AdminHotelController {
    private final HotelService hotels;

    public AdminHotelController(HotelService hotels) {
        this.hotels = hotels;
    }

    @GetMapping
    public HotelView get(@AuthenticationPrincipal AuthenticatedUser user) {
        return hotels.findAssigned(user.id());
    }

    @PutMapping
    public HotelView update(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody UpdateHotelRequest request) {
        return hotels.updateAssigned(user.id(), request);
    }
}
