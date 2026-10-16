package pe.edu.utp.icastay.payment;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.icastay.auth.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping("/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView simulate(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody SimulatePaymentRequest request) {
        return payments.simulate(user.id(), request);
    }
}
