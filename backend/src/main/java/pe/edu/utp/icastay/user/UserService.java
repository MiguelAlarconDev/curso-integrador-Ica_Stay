package pe.edu.utp.icastay.user;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class UserService {
    private final UserRepository users;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final org.slf4j.Logger AUDIT = org.slf4j.LoggerFactory.getLogger("icastay.audit.user-status");
    public UserService(UserRepository users, org.springframework.jdbc.core.JdbcTemplate jdbc) { this.users = users; this.jdbc = jdbc; }
    @Transactional(readOnly = true)
    public java.util.Optional<UserAccess> access(java.util.UUID id) { return users.findAccess(id); }

    @Transactional
    public SuperadminUserDetail changeStatus(java.util.UUID id, UserStatusRequest request,
            pe.edu.utp.icastay.auth.AuthenticatedUser actor) {
        // One database-wide lock, held until commit/rollback, also across application instances.
        jdbc.execute("SELECT pg_advisory_xact_lock(741003)");
        try {
            var currentActor = users.findAccess(actor.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
            if (!"ACTIVE".equals(currentActor.status()) || !"SUPER_ADMIN".equals(currentActor.role())
                    || currentActor.tokenVersion() != actor.tokenVersion()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (request == null || !("ACTIVE".equals(request.status()) || "INACTIVE".equals(request.status()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "estado invalido");
            }
            var target = users.findAccess(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if ("INACTIVE".equals(request.status())) {
                if (id.equals(actor.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no puedes desactivar tu propia cuenta");
                if ("SUPER_ADMIN".equals(target.role()) && "ACTIVE".equals(target.status())
                        && users.countByRoleAndStatus("SUPER_ADMIN", "ACTIVE") <= 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no puedes desactivar al ultimo SUPER_ADMIN activo");
                }
            }
            boolean changed = !target.status().equals(request.status());
            if (changed) users.changeStatus(id, request.status());
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        AUDIT.info("actor={} target={} result={}", actor.id(), id,
                            status == STATUS_COMMITTED ? (changed ? "CHANGED" : "UNCHANGED") : "ROLLED_BACK");
                    }
                });
            return findGlobal(id);
        } catch (RuntimeException exception) {
            AUDIT.warn("actor={} target={} result=REJECTED", actor.id(), id);
            throw exception;
        }
    }
    @Transactional(readOnly = true)
    public SuperadminUserDetail findGlobal(java.util.UUID id) {
        return users.findGlobalDetail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @Transactional(readOnly = true)
    public SuperadminUserPage listGlobal(int page, String q, String role, String status) {
        q = q == null ? "" : q.strip();
        if (page < 0 || (long) page * 20 > Integer.MAX_VALUE || q.length() > 254
                || (role != null && !Set.of("USER", "HOTEL_ADMIN", "SUPER_ADMIN").contains(role))
                || (status != null && !Set.of("ACTIVE", "INACTIVE").contains(status))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filtros o paginacion invalidos");
        }
        var result = users.search(q, role, status, PageRequest.of(page, 20));
        return new SuperadminUserPage(result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }
}
