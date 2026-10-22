package pe.edu.utp.icastay.auth;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenService jwtTokenService,
            CorsConfigurationSource corsConfigurationSource, pe.edu.utp.icastay.user.UserService users,
            @Value("${icastay.cors.allowed-origin}") String allowedOrigin) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preserve the original status when the container dispatches to /error.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/ai/chat").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai/conversations/current").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/hotels", "/api/v1/hotels/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/v1/superadmin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/hotel/reservations", "/api/v1/admin/hotel/reservations/**")
                            .hasRole("HOTEL_ADMIN")
                        .requestMatchers("/api/v1/admin/hotel", "/api/v1/admin/hotel/rooms", "/api/v1/admin/hotel/rooms/**").hasRole("HOTEL_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/simulate").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reservations").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/reservations/me", "/api/v1/reservations/*").hasRole("USER")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService, users),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new pe.edu.utp.icastay.ai.AiCookieCsrfFilter(allowedOrigin),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${icastay.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Authorization", "Accept"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Credentials only for the exact AI routes. Preserve all other API CORS rules.
        if (allowedOrigin.contains("*") || !allowedOrigin.matches("https?://[^/]+")) {
            throw new IllegalArgumentException("CORS requiere un origen exacto");
        }
        CorsConfiguration chat = new CorsConfiguration(configuration);
        chat.setAllowedMethods(List.of("POST", "OPTIONS"));
        chat.setAllowCredentials(true);
        CorsConfiguration history = new CorsConfiguration(chat);
        history.setAllowedMethods(List.of("GET", "OPTIONS"));
        source.registerCorsConfiguration("/api/v1/ai/chat", chat);
        source.registerCorsConfiguration("/api/v1/ai/conversations/current", history);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
