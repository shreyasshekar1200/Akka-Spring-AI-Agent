package com.rag.rag_agent.actors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.GroupRouter;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.Routers;

public class RouterActor extends AbstractBehavior<LLMActor.Command> {

    // The router doesn't have its own logic; it just forwards messages
    private final GroupRouter<LLMActor.Command> groupRouter;
    private final ActorRef<LLMActor.Command> router;

    public static Behavior<LLMActor.Command> create() {
        return Behaviors.setup(RouterActor::new);
    }

    private RouterActor(ActorContext<LLMActor.Command> context) {
        super(context);
        // 1. Create a Group Router
        // It looks for ANY actor in the cluster with the "llm-service" key
        this.groupRouter = Routers.group(LLMActor.SERVICE_KEY);

        // 2. Spawn the router as a child actor
        this.router = context.spawn(groupRouter, "worker-router");

        context
            .getLog()
            .info("🚦 Router is ready. Looking for workers in the cluster...");
    }

    @Override
    public Receive<LLMActor.Command> createReceive() {
        return newReceiveBuilder()
            // Forward ANY message we get to the router
            // The router will pick a random available worker (Node 1 or Node 2)
            .onAnyMessage(msg -> {
                router.tell(msg);
                return this;
            })
            .build();
    }
}
