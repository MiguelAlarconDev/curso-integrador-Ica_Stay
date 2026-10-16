package pe.edu.utp.icastay.payment;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.reservation.ReservationService;

@Service
public class PaymentService {
    private final ReservationService reservations;
    private final PaymentAttemptRepository attempts;

    public PaymentService(ReservationService reservations, PaymentAttemptRepository attempts) {
        this.reservations = reservations;
        this.attempts = attempts;
    }

    @Transactional
    public PaymentView simulate(UUID guestId, SimulatePaymentRequest request) {
        if (request == null || request.reservationId() == null || request.result() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reservationId y result son obligatorios");
        }
        reservations.processPayment(guestId, request.reservationId(), request.result() == PaymentResult.APPROVED);
        // The room and reservation locks remain held until this transaction commits.
        if (attempts.existsByReservationIdAndStatus(request.reservationId(), "APPROVED")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "reserva ya pagada");
        }
        PaymentAttemptEntity attempt = attempts.saveAndFlush(
                PaymentAttemptEntity.simulated(request.reservationId(), request.result(), Instant.now()));
        return new PaymentView(attempt.getId(), request.result(), attempt.getExternalReference());
    }
}
