package pe.edu.utp.icastay.hotel;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HotelRepository extends JpaRepository<HotelEntity, UUID> {
    @Query("select new pe.edu.utp.icastay.hotel.SuperadminHotelDetail("
            + "h.id, h.name, h.address, h.city, h.status, u.id, u.name, u.email, h.description)"
            + " from HotelEntity h join UserEntity u on u.id = h.adminUserId where h.id = :id")
    Optional<SuperadminHotelDetail> findGlobalDetail(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HotelEntity h where h.id = :id")
    Optional<HotelEntity> findForUpdate(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HotelEntity h where h.adminUserId = :adminId")
    Optional<HotelEntity> findAssignedForUpdate(@Param("adminId") UUID adminId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select h from HotelEntity h where h.id = :id and h.status = 'ACTIVE'")
    Optional<HotelEntity> findActiveForBooking(@Param("id") UUID id);

    String GLOBAL_FROM = " from HotelEntity h join UserEntity u on u.id = h.adminUserId ";
    String GLOBAL_FILTERS = " where (:name = '' or locate(lower(:name), lower(h.name)) > 0) "
            + "and (:status is null or h.status = :status) ";

    @Query(value = "select new pe.edu.utp.icastay.hotel.SuperadminHotelView("
            + "h.id, h.name, h.address, h.city, h.status, u.id, u.name, u.email)"
            + GLOBAL_FROM + GLOBAL_FILTERS + "order by h.name asc, h.id asc",
            countQuery = "select count(h.id)" + GLOBAL_FROM + GLOBAL_FILTERS)
    Page<SuperadminHotelView> searchGlobal(@Param("name") String name,
            @Param("status") String status, Pageable pageable);

    Page<HotelEntity> findByStatus(String status, Pageable pageable);

    @Query("""
            select h from HotelEntity h where h.status = 'ACTIVE'
            and (:name = '' or locate(lower(:name), lower(h.name)) > 0)
            and (:city = '' or lower(h.city) = lower(:city))
            """)
    Page<HotelEntity> searchActive(@Param("name") String name, @Param("city") String city, Pageable pageable);

    Optional<HotelEntity> findByIdAndStatus(UUID id, String status);

    Optional<HotelEntity> findByAdminUserId(UUID adminUserId);
}
