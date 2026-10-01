package pe.edu.utp.icastay.reservation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.icastay.user.UserEntity;

interface ReservationUserRepository extends JpaRepository<UserEntity, UUID> {
    boolean existsByIdAndStatus(UUID id, String status);
}
