package pe.edu.utp.icastay.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utp.icastay.user.UserEntity;

interface AuthUserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmailAndStatus(String email, String status);
}
