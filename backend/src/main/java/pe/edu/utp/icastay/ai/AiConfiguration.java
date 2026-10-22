package pe.edu.utp.icastay.ai;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @Bean
    HttpClient ollamaHttpClient(AiProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    OllamaClient ollamaClient(AiProperties properties, ObjectMapper mapper,
            @org.springframework.beans.factory.annotation.Qualifier("ollamaHttpClient") HttpClient client) {
        return new OllamaClient(client, mapper, properties);
    }
}
