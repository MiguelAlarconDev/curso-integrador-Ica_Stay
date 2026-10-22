package pe.edu.utp.icastay.ai;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {
    private final AiChatService service;

    public AiChatController(AiChatService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@RequestBody AiChatRequest request) {
        return service.chat(request);
    }
}
