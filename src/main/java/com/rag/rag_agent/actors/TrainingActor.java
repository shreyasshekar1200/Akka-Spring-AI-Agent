package com.rag.rag_agent.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import java.io.File;

public class TrainingActor extends AbstractBehavior<TrainingActor.Command> {

    public interface Command {}

    public static class StartTraining implements Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(TrainingActor::new);
    }

    private TrainingActor(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(StartTraining.class, this::onStartTraining)
            .build();
    }

    private Behavior<Command> onStartTraining(StartTraining msg) {
        getContext()
            .getLog()
            .info("Freeing GPU and starting QLoRA training...");

        new Thread(() -> {
            try {
                // 1. Tell Ollama to unload the model to free VRAM for training
                new ProcessBuilder("ollama", "stop", "llama3.2")
                    .start()
                    .waitFor();

                // 2. Run the Python training script from your ai_engine folder
                ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    "ai_engine/train_engine.py"
                );
                pb.directory(new File(System.getProperty("user.dir")));
                pb.inheritIO(); // Pipe Python output directly to Java console

                Process p = pb.start();
                int exitCode = p.waitFor();

                if (exitCode == 0) {
                    getContext()
                        .getLog()
                        .info("Training successful. Reloading model...");
                    new ProcessBuilder("ollama", "run", "llama3.2").start();
                }
            } catch (Exception e) {
                getContext().getLog().error("Training Process Failed", e);
            }
        })
            .start();

        return this;
    }
}
