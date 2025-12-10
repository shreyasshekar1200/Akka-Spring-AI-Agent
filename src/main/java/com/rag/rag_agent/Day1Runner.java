package com.rag.rag_agent;

import java.util.List;
import java.util.Scanner;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class Day1Runner implements CommandLineRunner {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("classpath:test-doc.txt")
    private Resource testDoc;

    public Day1Runner(
        VectorStore vectorStore,
        ChatClient.Builder chatClientBuilder
    ) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- INGESTION STARTED ---");

        // 1. Ingest content
        TikaDocumentReader reader = new TikaDocumentReader(testDoc);
        List<Document> documents = reader.get();
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        this.vectorStore.add(chunks);

        System.out.println(
            "--- INGESTION COMPLETE: " + chunks.size() + " chunks stored ---"
        );
        System.out.println(
            "Server is ready! Go to: http://localhost:8080/ask?query=YourQuestion"
        );
    }
}
