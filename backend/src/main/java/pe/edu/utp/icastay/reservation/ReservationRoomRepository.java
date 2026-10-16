package pe.edu.utp.icastay.reservation;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.icastay.room.RoomEntity;

interface ReservationRoomRepository extends JpaRepository<RoomEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RoomEntity r where r.id = :id")
    Optional<RoomEntity> findForPayment(@org.springframework.data.repository.query.Param("id") UUID id);

    // Serialize booking with administrative updates and status transitions.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RoomEntity> findByIdAndStatus(UUID id, String status);
}
