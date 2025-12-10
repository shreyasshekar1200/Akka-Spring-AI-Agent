package com.rag.rag_agent.config;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.rag.rag_agent.RagService;
import com.rag.rag_agent.actors.LLMActor;
import com.rag.rag_agent.actors.RouterActor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AkkaConfig {

    /**
     * This method starts the Akka "Universe" (Actor System).
     * It also spawns our Main Actor (LLMActor) as the guardian of the system.
     */
    @Bean
    public ActorSystem<LLMActor.Command> actorSystem(RagService ragService) {
        Behavior<LLMActor.Command> rootBehavior = Behaviors.setup(context -> {
            context.spawn(LLMActor.create(ragService), "llm-worker");
            return RouterActor.create();
        });

        // Create the system with the name "RagCluster"
        ActorSystem<LLMActor.Command> system = ActorSystem.create(
            rootBehavior,
            "RagCluster"
        );

        // Log that we are live
        system.log().info("Cluster System Started with Router + Worker");

        return system;
    }
}
