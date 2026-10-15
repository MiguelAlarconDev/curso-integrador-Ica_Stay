package pe.edu.utp.icastay.reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReservationRepository extends JpaRepository<ReservationEntity, UUID> {

    String ADMIN_SELECT = """
            select new pe.edu.utp.icastay.reservation.AdminReservationView(
                r.id, r.checkIn, r.checkOut, r.guests, r.status, r.expiresAt,
                r.nightlyPriceSnapshot, r.totalAmount, r.currency, r.createdAt,
                room.id, room.number, guest.id, guest.name, guest.email)
            """;
    String ADMIN_FROM = """
            from ReservationEntity r
            join RoomEntity room on room.id = r.roomId
            join UserEntity guest on guest.id = r.guestUserId
            where room.hotelId = :hotelId
            """;
    String ADMIN_FILTERS = """
            and r.status = coalesce(:status, r.status)
            and r.checkOut > coalesce(:fromDate, r.checkIn)
            and r.checkIn <= coalesce(:toDate, r.checkIn)
            """;

    @Query(value = ADMIN_SELECT + ADMIN_FROM + ADMIN_FILTERS + " order by r.createdAt desc, r.id desc",
            countQuery = "select count(r.id) " + ADMIN_FROM + ADMIN_FILTERS)
    Page<AdminReservationView> findAdminPage(@Param("hotelId") UUID hotelId,
            @Param("status") String status, @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate, Pageable pageable);

    @Query(ADMIN_SELECT + ADMIN_FROM + " and r.id = :id")
    Optional<AdminReservationView> findAdminDetail(@Param("hotelId") UUID hotelId, @Param("id") UUID id);

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
