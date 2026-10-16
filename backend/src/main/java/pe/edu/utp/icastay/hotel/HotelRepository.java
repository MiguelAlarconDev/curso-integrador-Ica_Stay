package pe.edu.utp.icastay.hotel;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HotelRepository extends JpaRepository<HotelEntity, UUID> {
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
