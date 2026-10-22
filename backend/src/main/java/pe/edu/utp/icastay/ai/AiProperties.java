package pe.edu.utp.icastay.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("icastay.ai")
public record AiProperties(URI baseUrl, String model, Duration connectTimeout,
        Duration responseTimeout, int maxTokens, int maxConcurrentRequests) {
    public AiProperties {
        if (baseUrl == null || !("http".equalsIgnoreCase(baseUrl.getScheme())
                || "https".equalsIgnoreCase(baseUrl.getScheme())) || baseUrl.getHost() == null
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || !(baseUrl.getPath().isEmpty() || "/".equals(baseUrl.getPath()))
                || model == null || model.isBlank() || model.length() > 128
                || connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || responseTimeout == null || responseTimeout.isNegative() || responseTimeout.isZero()
                || maxTokens < 1 || maxConcurrentRequests < 1) {
            throw new IllegalArgumentException("Configuración de IA inválida");
        }
    }
}
