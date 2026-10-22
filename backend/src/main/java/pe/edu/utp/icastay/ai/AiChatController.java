package pe.edu.utp.icastay.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.ai.history.AiHistoryService;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {
    private final AiChatService service;
    private final AiSessionService sessions;
    private final AiHistoryService history;
    private final ObjectMapper mapper;

    public AiChatController(AiChatService service, AiSessionService sessions, AiHistoryService history, ObjectMapper mapper) {
        this.service = service;
        this.sessions = sessions;
        this.history = history;
        this.mapper = mapper;
    }

    @PostMapping(value = "/chat", consumes = "application/json", produces = "application/json")
    public void chat(@RequestBody AiChatRequest request, HttpServletRequest httpRequest, HttpServletResponse response) throws IOException {
        var session = sessions.read(httpRequest).orElseGet(sessions::create);
        var answer = service.chat(request, session);
        byte[] json = mapper.writeValueAsBytes(answer);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Set-Cookie", sessions.cookie(session));
        // Only persist after a successful write/flush. A detected client disconnect throws IOException.
        // A TCP flush is not an acknowledgement by the browser; undetected disconnects remain possible.
        response.getOutputStream().write(json);
        response.flushBuffer();
        service.completed(request, answer, session);
    }

    @GetMapping("/conversations/current")
    public AiConversationPage current(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, HttpServletRequest request, HttpServletResponse response) {
        if (page < 0 || page > 1000 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paginación inválida");
        response.setHeader("Cache-Control", "no-store");
        var session = sessions.read(request);
        if (session.isEmpty()) {
            if (request.getCookies() != null) response.addHeader("Set-Cookie", sessions.clearCookie());
            return new AiConversationPage(null, java.util.List.of(), page, size, false);
        }
        var historyPage = history.findActive(session.get().conversationId(), session.get().secret(), page, size);
        if (!historyPage.available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El historial no está disponible temporalmente");
        var content = historyPage.content().stream().map(item -> new AiConversationPage.Exchange(
                item.question(), item.answer(), item.createdAt(), item.expiresAt())).toList();
        return new AiConversationPage(session.get().conversationId(), content, page, size, historyPage.hasNext());
    }
}
