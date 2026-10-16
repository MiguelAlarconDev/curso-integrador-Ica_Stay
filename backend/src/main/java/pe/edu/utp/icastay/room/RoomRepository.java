package pe.edu.utp.icastay.room;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomRepository extends JpaRepository<RoomEntity, UUID> {
    @Query("""
            select room from RoomEntity room
            where room.hotelId = :hotelId and room.status = 'ACTIVE' and room.capacity >= :guests
            and not exists (select r.id from ReservationEntity r where r.roomId = room.id
                and r.checkIn < :checkOut and r.checkOut > :checkIn
                and (r.status = 'CONFIRMED' or (r.status = 'PENDING_PAYMENT' and r.expiresAt > :now)))
            order by room.number asc, room.id asc
            """)
    List<RoomEntity> findAvailable(UUID hotelId, java.time.LocalDate checkIn,
            java.time.LocalDate checkOut, int guests, java.time.Instant now);

    List<RoomEntity> findByHotelIdAndStatusOrderByNumberAscIdAsc(UUID hotelId, String status);

    List<RoomEntity> findByHotelIdOrderByNumberAscIdAsc(UUID hotelId);

    Optional<RoomEntity> findByIdAndHotelId(UUID id, UUID hotelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.id = :id and r.hotelId = :hotelId")
    Optional<RoomEntity> findOwnedForUpdate(UUID id, UUID hotelId);
}
