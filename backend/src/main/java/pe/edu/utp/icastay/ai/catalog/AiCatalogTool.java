package pe.edu.utp.icastay.ai.catalog;

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.ai.AiChatResponse;

@Service
public class AiCatalogTool {
    private final AiCatalogReader reader;
    private final ExecutorService workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon(true).name("ai-catalog-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    public AiCatalogTool(AiCatalogReader reader) { this.reader = reader; }

    public Optional<String> answer(String message) {
        return response(message).map(AiChatResponse::answer);
    }

    public Optional<AiChatResponse> response(String message) {
        var parsed = AiCatalogQuery.parse(message);
        if (parsed.isEmpty()) return Optional.empty();
        var plan = parsed.get();
        if (plan.query().isEmpty()) return Optional.of(new AiChatResponse(plan.clarification(), "icastay-public-catalog-v1"));
        Future<AiChatResponse> pending;
        try { pending = workers.submit(() -> reader.readResponse(plan.query().orElseThrow())); }
        catch (RejectedExecutionException busy) { throw unavailable(); }
        try { return Optional.of(pending.get(5, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw unavailable();
        } catch (TimeoutException timeout) { pending.cancel(true); throw unavailable(); }
        catch (ExecutionException failure) {
            if (failure.getCause() instanceof ResponseStatusException invalid && invalid.getStatusCode().value() == 400)
                return Optional.of(new AiChatResponse("No pude validar las fechas. Usa entrada y salida futuras AAAA-MM-DD, con salida posterior a entrada. " + AiCatalogQuery.HELP, "icastay-public-catalog-v1"));
            throw unavailable();
        }
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No pude consultar el catálogo en este momento. Reintenta más tarde; no hay datos confirmados.");
    }
    @PreDestroy public void close() { workers.shutdownNow(); }
}
