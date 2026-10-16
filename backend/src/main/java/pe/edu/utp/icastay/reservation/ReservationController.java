package pe.edu.utp.icastay.reservation;

import pe.edu.utp.icastay.auth.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationView create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody CreateReservationRequest request) {
        return reservationService.create(user.id(), request);
    }

    @GetMapping("/me")
    public UserReservationPage listOwn(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page) {
        return reservationService.listOwn(user.id(), page);
    }

    @GetMapping("/{id}")
    public UserReservationView getOwn(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return reservationService.findOwn(user.id(), id);
    }
}
