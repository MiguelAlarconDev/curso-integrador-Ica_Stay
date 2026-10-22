package pe.edu.utp.icastay.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AiConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AiConfiguration.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues("icastay.ai.base-url=http://localhost:11434",
                    "icastay.ai.model=qwen2.5:1.5b", "icastay.ai.connect-timeout=3s",
                    "icastay.ai.response-timeout=60s", "icastay.ai.max-tokens=256",
                    "icastay.ai.max-concurrent-requests=1");

    @Test
    void bindsExternalPropertiesWithoutConnectingToOllama() {
        context.run(application -> {
            assertNull(application.getStartupFailure());
            var properties = application.getBean(AiProperties.class);
            assertEquals(Duration.ofSeconds(3), properties.connectTimeout());
            assertEquals(Duration.ofSeconds(60), properties.responseTimeout());
            assertEquals(256, properties.maxTokens());
            assertNotNull(application.getBean(OllamaClient.class));
        });
    }

    @Test
    void invalidConfigurationFailsBeforeServingRequests() {
        for (String invalid : new String[] {"icastay.ai.max-concurrent-requests=0",
                "icastay.ai.max-tokens=0", "icastay.ai.connect-timeout=0s",
                "icastay.ai.response-timeout=-1s", "icastay.ai.model=",
                "icastay.ai.base-url=file:///local", "icastay.ai.base-url=http://localhost:11434/other"}) {
            context.withPropertyValues(invalid).run(application -> assertNotNull(application.getStartupFailure()));
        }
    }
}
