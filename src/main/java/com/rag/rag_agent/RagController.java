package com.rag.rag_agent;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import com.rag.rag_agent.actors.LLMActor;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@RestController
public class RagController {

    private final ActorSystem<LLMActor.Command> actorSystem;
    private final RagService ragService; // Kept for upload only

    // Inject the ActorSystem we defined in AkkaConfig
    public RagController(
        ActorSystem<LLMActor.Command> actorSystem,
        RagService ragService
    ) {
        this.actorSystem = actorSystem;
        this.ragService = ragService;
    }

    // Endpoint: http://localhost:8080/ask?query=...
    @GetMapping(value = "/ask") // Removed STREAM_VALUE for simplicity in this phase
    public CompletionStage<String> askQuestion(
        @RequestParam(value = "query", defaultValue = "Hello") String query
    ) {
        // The "Ask" Pattern:
        // 1. Send 'AskQuestion' message to the actorSystem
        // 2. Wait up to 30 seconds for a reply
        // 3. Return the result (Future)
        return AskPattern.ask(
            actorSystem,
            replyTo -> new LLMActor.AskQuestion(query, replyTo),
            Duration.ofSeconds(60), // Timeout
            actorSystem.scheduler()
        );
    }

    // We keep upload direct for now (Simplicity)
    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file) {
        return ragService.ingestFile(file);
    }
}
