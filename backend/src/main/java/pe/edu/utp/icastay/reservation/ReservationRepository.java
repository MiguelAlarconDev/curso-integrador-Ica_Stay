package pe.edu.utp.icastay.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

    @Query("""
            select r
            from ReservationEntity r
            where r.roomId = :roomId
              and r.checkIn < :requestedCheckOut
              and r.checkOut > :requestedCheckIn
            """)
    List<ReservationEntity> findOverlapping(
            @Param("roomId") UUID roomId,
            @Param("requestedCheckIn") LocalDate requestedCheckIn,
            @Param("requestedCheckOut") LocalDate requestedCheckOut);
}
