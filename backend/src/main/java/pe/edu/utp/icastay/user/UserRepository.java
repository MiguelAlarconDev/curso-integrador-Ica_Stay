package pe.edu.utp.icastay.user;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
interface UserRepository extends Repository<UserEntity, UUID> {
    @Query("select new pe.edu.utp.icastay.user.UserAccess(u.id, u.email, u.role, u.status, u.tokenVersion) from UserEntity u where u.id = :id")
    java.util.Optional<UserAccess> findAccess(@Param("id") UUID id);
    long countByRoleAndStatus(String role, String status);
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserEntity u set u.status = :status, u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int changeStatus(@Param("id") UUID id, @Param("status") String status);

    @Query("""
        select new pe.edu.utp.icastay.user.SuperadminUserDetail(u.id, u.name, u.email, u.role, u.status, h.id, h.name, h.status)
        from UserEntity u left join HotelEntity h on h.adminUserId = u.id and u.role = 'HOTEL_ADMIN'
        where u.id = :id
        """)
    java.util.Optional<SuperadminUserDetail> findGlobalDetail(@Param("id") UUID id);

    @Query(value = """
        select new pe.edu.utp.icastay.user.SuperadminUserView(u.id, u.name, u.email, u.role, u.status)
        from UserEntity u
        where (:q = '' or locate(lower(:q), lower(u.name)) > 0 or locate(lower(:q), lower(u.email)) > 0)
          and (:role is null or u.role = :role) and (:status is null or u.status = :status)
        order by u.name, u.id
        """, countQuery = """
        select count(u) from UserEntity u
        where (:q = '' or locate(lower(:q), lower(u.name)) > 0 or locate(lower(:q), lower(u.email)) > 0)
          and (:role is null or u.role = :role) and (:status is null or u.status = :status)
        """)
    Page<SuperadminUserView> search(@Param("q") String q, @Param("role") String role,
        @Param("status") String status, Pageable pageable);
}
