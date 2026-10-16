package pe.edu.utp.icastay.payment;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.UUID;

public record SimulatePaymentRequest(UUID reservationId, PaymentResult result) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("campo no permitido");
    }
}
