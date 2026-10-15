package pe.edu.utp.icastay.room;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomRepository extends JpaRepository<RoomEntity, UUID> {
    List<RoomEntity> findByHotelIdAndStatusOrderByNumberAscIdAsc(UUID hotelId, String status);

    List<RoomEntity> findByHotelIdOrderByNumberAscIdAsc(UUID hotelId);

    Optional<RoomEntity> findByIdAndHotelId(UUID id, UUID hotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.id = :id and r.hotelId = :hotelId")
    Optional<RoomEntity> findOwnedForUpdate(UUID id, UUID hotelId);
}
