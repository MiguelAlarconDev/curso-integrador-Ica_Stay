package pe.edu.utp.icastay.superadmin;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.icastay.user.UserService;
import pe.edu.utp.icastay.user.SuperadminUserPage;
@RestController
@RequestMapping("/api/v1/superadmin/users")
public class SuperadminUserController {
    private final UserService users;
    public SuperadminUserController(UserService users) { this.users = users; }
    @PatchMapping("/{id}/status")
    public pe.edu.utp.icastay.user.SuperadminUserDetail status(@PathVariable java.util.UUID id,
            @RequestBody java.util.Map<String, Object> body,
            @org.springframework.security.core.annotation.AuthenticationPrincipal pe.edu.utp.icastay.auth.AuthenticatedUser actor) {
        if (body == null || body.size() != 1 || !(body.get("status") instanceof String status)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "estado invalido");
        }
        return users.changeStatus(id, new pe.edu.utp.icastay.user.UserStatusRequest(status), actor);
    }
    @GetMapping("/{id}")
    public pe.edu.utp.icastay.user.SuperadminUserDetail detail(@PathVariable java.util.UUID id) {
        return users.findGlobal(id);
    }
    @GetMapping
    public SuperadminUserPage list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String q, @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return users.listGlobal(page, q, role, status);
    }
}
