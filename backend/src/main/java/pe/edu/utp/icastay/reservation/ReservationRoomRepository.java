package pe.edu.utp.icastay.reservation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.icastay.room.RoomEntity;

interface ReservationRoomRepository extends JpaRepository<RoomEntity, UUID> {
    Optional<RoomEntity> findByIdAndStatus(UUID id, String status);
}
