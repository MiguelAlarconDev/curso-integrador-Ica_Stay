package pe.edu.utp.icastay.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    private final pe.edu.utp.icastay.user.UserService users;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, pe.edu.utp.icastay.user.UserService users) {
        this.users = users;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            authenticate(authorization.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Map<String, Object> claims = jwtTokenService.validateAndReadClaims(token);
            String email = String.valueOf(claims.get("sub"));
            UUID userId = UUID.fromString(String.valueOf(claims.get("userId")));
            String role = String.valueOf(claims.get("role"));
            Object versionClaim = claims.getOrDefault("ver", 0L);
            if (!(versionClaim instanceof Long || versionClaim instanceof Integer)) throw new IllegalArgumentException("JWT invalido");
            long version = ((Number) versionClaim).longValue();
            var account = users.access(userId).orElseThrow(() -> new IllegalArgumentException("cuenta invalida"));
            if (!"ACTIVE".equals(account.status()) || account.tokenVersion() != version
                    || !account.role().equals(role) || !account.email().equals(email)) {
                throw new IllegalArgumentException("sesion revocada");
            }
            AuthenticatedUser principal = new AuthenticatedUser(userId, email, role, version);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ignored) {
            SecurityContextHolder.clearContext();
        }
    }
}
