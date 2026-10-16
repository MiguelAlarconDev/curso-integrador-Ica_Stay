package pe.edu.utp.icastay.reservation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.auth.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/admin/hotel/reservations")
public class AdminReservationController {
    private final ReservationService reservations;

    public AdminReservationController(ReservationService reservations) { this.reservations = reservations; }

    @GetMapping
    public AdminReservationPage list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return reservations.listAssigned(user.id(), page, size, status, date(from), date(to));
    }

    @GetMapping("/{id}")
    public AdminReservationView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return reservations.findAssigned(user.id(), id);
    }

    private LocalDate date(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            if (date.getYear() < 1) throw new IllegalArgumentException();
            return date;
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fecha invalida: use YYYY-MM-DD");
        }
    }
}
