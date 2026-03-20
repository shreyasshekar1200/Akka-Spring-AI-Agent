package com.rag.rag_agent.config;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import com.rag.rag_agent.RagService;
import com.rag.rag_agent.actors.LLMActor;
import com.rag.rag_agent.actors.RouterActor;
import com.rag.rag_agent.actors.TrainingActor;
import com.typesafe.config.Config; // <--- ADD THIS
import com.typesafe.config.ConfigFactory; // <--- ADD THIS
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AkkaConfig {

    @Bean
    public ActorSystem<LLMActor.Command> actorSystem(RagService ragService) {
        Behavior<LLMActor.Command> rootBehavior = Behaviors.setup(context -> {
            context.spawn(LLMActor.create(ragService), "llm-worker");
            return RouterActor.create();
        });

        return ActorSystem.create(rootBehavior, "RagCluster");
    }

    @Bean
    public ActorSystem<TrainingActor.Command> trainingSystem() {
        // This configuration ensures the Training system doesn't try to steal
        // the 2551 port from the main Cluster system.
        Config trainingConfig = ConfigFactory.parseString(
            "akka.remote.artery.canonical.port = 2553\n" +
                "akka.cluster.seed-nodes = []"
        ).withFallback(ConfigFactory.load());

        return ActorSystem.create(
            TrainingActor.create(),
            "TrainingSystem",
            trainingConfig
        );
    }
}
