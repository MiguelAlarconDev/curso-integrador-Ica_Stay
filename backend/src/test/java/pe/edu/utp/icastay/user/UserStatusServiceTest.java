package pe.edu.utp.icastay.user;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.edu.utp.icastay.auth.AuthenticatedUser;
class UserStatusServiceTest {
    @Test void refusesLastActiveSuperadminBeforeUpdate() {
        var repo=mock(UserRepository.class);var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        UUID actor=UUID.randomUUID(),target=UUID.randomUUID();
        when(repo.findAccess(actor)).thenReturn(Optional.of(new UserAccess(actor,"actor@test","SUPER_ADMIN","ACTIVE",0)));
        when(repo.findAccess(target)).thenReturn(Optional.of(new UserAccess(target,"target@test","SUPER_ADMIN","ACTIVE",0)));
        when(repo.countByRoleAndStatus("SUPER_ADMIN","ACTIVE")).thenReturn(1L);
        var failure=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new UserService(repo,jdbc).changeStatus(target,new UserStatusRequest("INACTIVE"),new AuthenticatedUser(actor,"actor@test","SUPER_ADMIN")));
        assertEquals(400,failure.getStatusCode().value());verify(repo,never()).changeStatus(any(),any());
        verify(jdbc).execute("SELECT pg_advisory_xact_lock(741003)");
    }
}
