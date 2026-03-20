package com.rag.rag_agent;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import com.rag.rag_agent.actors.LLMActor;
import com.rag.rag_agent.actors.TrainingActor;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class RagController {

    private final ActorSystem<LLMActor.Command> actorSystem;
    private final ActorSystem<TrainingActor.Command> trainingSystem;
    private final RagService ragService;

    public RagController(
        ActorSystem<LLMActor.Command> actorSystem,
        ActorSystem<TrainingActor.Command> trainingSystem,
        RagService ragService
    ) {
        this.actorSystem = actorSystem;
        this.trainingSystem = trainingSystem;
        this.ragService = ragService;
    }

    @GetMapping("/ask")
    public CompletionStage<String> askQuestion(@RequestParam String query) {
        return AskPattern.ask(
            actorSystem,
            replyTo -> new LLMActor.AskQuestion(query, replyTo),
            Duration.ofSeconds(60),
            actorSystem.scheduler()
        );
    }

    @PostMapping("/upload")
    public String uploadDocuments(
        @RequestParam("files") List<MultipartFile> files
    ) {
        return ragService.ingestFiles(files);
    }

    @PostMapping("/train")
    public String triggerTraining() {
        trainingSystem.tell(new TrainingActor.StartTraining());
        return "Fine-tuning sequence initiated. Check console for VRAM status.";
    }
}
