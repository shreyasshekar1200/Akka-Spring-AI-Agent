package com.rag.rag_agent;

import java.util.List;
import java.util.stream.Collectors;
// 1. Clean Imports
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
// We removed InMemoryChatMemory import to avoid the error
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@Service
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(
        VectorStore vectorStore,
        ChatClient.Builder chatClientBuilder,
        ChatMemory chatMemory // <--- FIX 1: Let Spring inject the memory
    ) {
        this.vectorStore = vectorStore;

        // <--- FIX 2: Use the Builder Pattern instead of 'new'
        this.chatClient = chatClientBuilder
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            )
            .build();
    }

    public String ingestFile(MultipartFile file) {
        try {
            List<Document> documents;
            String filename = file.getOriginalFilename();

            if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
                    new ByteArrayResource(file.getBytes())
                );
                documents = pdfReader.get();
            } else {
                TikaDocumentReader tikaReader = new TikaDocumentReader(
                    new ByteArrayResource(file.getBytes())
                );
                documents = tikaReader.get();
            }

            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocs = splitter.apply(documents);
            vectorStore.add(splitDocs);

            return (
                "Success! Processed " +
                splitDocs.size() +
                " chunks from " +
                filename
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to ingest file", e);
        }
    }

    public Flux<String> generateAnswer(String query) {
        List<Document> similarDocs = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(50).build()
        );

        String context = similarDocs.isEmpty()
            ? "No specific context found."
            : similarDocs
                  .stream()
                  .map(Document::getText)
                  .collect(Collectors.joining("\n\n"));

        String prompt = String.format(
            """
            You are a helpful assistant. Answer the question based on the following context.

            Context:
            %s

            Question:
            %s
            """,
            context,
            query
        );

        // Simple prompt call (The Advisor handles the memory keys automatically now)
        return chatClient.prompt(prompt).stream().content();
    }
}
