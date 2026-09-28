package pe.edu.utp.icastay.room;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomRepository extends JpaRepository<RoomEntity, UUID> {
    List<RoomEntity> findByHotelIdAndStatusOrderByNumberAscIdAsc(UUID hotelId, String status);
}
