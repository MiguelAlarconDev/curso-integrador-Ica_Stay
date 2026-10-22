package pe.edu.utp.icastay.ai;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.web.filter.OncePerRequestFilter;

/** Origin-based CSRF protection scoped exclusively to the public cookie-based AI API. */
public class AiCookieCsrfFilter extends OncePerRequestFilter {
    private final String allowedOrigin;
    public AiCookieCsrfFilter(String allowedOrigin) { this.allowedOrigin = allowedOrigin; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath().isEmpty() ? request.getRequestURI() : request.getServletPath();
        boolean chat = "POST".equals(request.getMethod()) && "/api/v1/ai/chat".equals(path);
        boolean history = "GET".equals(request.getMethod()) && "/api/v1/ai/conversations/current".equals(path);
        if (chat || history) {
            response.setHeader("Cache-Control", "no-store");
            var origins = Collections.list(request.getHeaders("Origin"));
            boolean invalidOrigin = origins.size() > 1 || (!origins.isEmpty() && !allowedOrigin.equals(origins.getFirst()));
            boolean credentialed = request.getHeader("Cookie") != null || request.getCookies() != null;
            boolean missingRequiredOrigin = chat && origins.isEmpty()
                    && (credentialed || request.getHeader("Sec-Fetch-Site") != null);
            if (invalidOrigin || missingRequiredOrigin || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
                response.sendError(403); return;
            }
        }
        chain.doFilter(request, response);
    }
}
