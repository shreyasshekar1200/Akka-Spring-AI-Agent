package com.rag.rag_agent.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.rag.rag_agent.RagService;

public class LLMActor extends AbstractBehavior<LLMActor.Command> {

    // 1. Define a cluster-wide "Tag" for this actor
    public static final ServiceKey<Command> SERVICE_KEY = ServiceKey.create(
        Command.class,
        "llm-service"
    );

    // --- 1. Define the Commands (Messages) ---
    public interface Command {}

    // This is the message other nodes will send to us
    // It contains the 'query' and a 'replyTo' address so we know where to send the answer
    public record AskQuestion(
        String query,
        ActorRef<String> replyTo
    ) implements Command {}

    // --- 2. State (Dependencies) ---
    private final RagService ragService;

    // --- 3. Factory Method (How to create this Actor) ---
    public static Behavior<Command> create(RagService ragService) {
        return Behaviors.setup(context -> {
            context
                .getSystem()
                .receptionist()
                .tell(Receptionist.register(SERVICE_KEY, context.getSelf()));

            return new LLMActor(context, ragService);
        });
    }

    // Constructor
    private LLMActor(ActorContext<Command> context, RagService ragService) {
        super(context);
        this.ragService = ragService;
    }

    // --- 4. Message Handling (The Logic) ---
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AskQuestion.class, this::onAskQuestion)
            .build();
    }

    private Behavior<Command> onAskQuestion(AskQuestion command) {
        getContext()
            .getLog()
            .info("LLM Actor received task: {}", command.query);

        try {
            // Change from .generateAnswer(query) to a non-streaming call if possible,
            // OR simply simplify the retrieval:
            String fullAnswer = ragService
                .generateAnswer(command.query)
                .collectList() // Collect all chunks into a list first
                .map(list -> String.join("", list)) // Join them
                .block();

            command.replyTo.tell(fullAnswer);
        } catch (Exception e) {
            getContext().getLog().error("AI Failed", e);
            command.replyTo.tell("Error: " + e.getMessage());
        }
        return this;
    }
}
