package pe.edu.utp.icastay.hotel;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface HotelRepository extends JpaRepository<HotelEntity, UUID> {
    Page<HotelEntity> findByStatus(String status, Pageable pageable);

    Optional<HotelEntity> findByIdAndStatus(UUID id, String status);
}
