package pe.edu.utp.icastay.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, UUID> {
    boolean existsByReservationIdAndStatus(UUID reservationId, String status);
}
