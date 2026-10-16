package pe.edu.utp.icastay.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttemptEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "external_reference", unique = true, length = 160)
    private String externalReference;

    @Column(name = "provider_payment_id", unique = true, length = 160)
    private String providerPaymentId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentAttemptEntity() {
    }

    static PaymentAttemptEntity simulated(UUID reservationId, PaymentResult result, Instant now) {
        PaymentAttemptEntity attempt = new PaymentAttemptEntity();
        attempt.reservationId = reservationId;
        attempt.status = result.name();
        attempt.externalReference = "sim-" + UUID.randomUUID();
        attempt.createdAt = now;
        attempt.updatedAt = now;
        return attempt;
    }

    public UUID getId() { return id; }
    public String getExternalReference() { return externalReference; }
}
