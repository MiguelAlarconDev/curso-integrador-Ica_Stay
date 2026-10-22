package pe.edu.utp.icastay.ai.history;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiHistoryConfiguration {
    @Bean
    MongoClientSettingsBuilderCustomizer aiHistoryMongoLimits() {
        return builder -> builder.timeout(3, TimeUnit.SECONDS).retryReads(false).retryWrites(false)
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(2, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(pool -> pool.maxSize(5).maxWaitTime(2, TimeUnit.SECONDS));
    }
}
