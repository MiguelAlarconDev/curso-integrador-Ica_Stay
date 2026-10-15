package pe.edu.utp.icastay.reservation;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.icastay.room.RoomEntity;

interface ReservationRoomRepository extends JpaRepository<RoomEntity, UUID> {
    // Serialize booking with administrative updates and status transitions.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RoomEntity> findByIdAndStatus(UUID id, String status);
}
