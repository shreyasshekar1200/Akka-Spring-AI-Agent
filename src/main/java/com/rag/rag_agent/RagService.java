package com.rag.rag_agent;

import com.rag.rag_agent.tools.SearchTools;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
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
    private final SearchTools searchTools;

    public RagService(
        VectorStore vectorStore,
        ChatClient.Builder chatClientBuilder,
        ChatMemory chatMemory,
        SearchTools searchTools
    ) {
        this.vectorStore = vectorStore;
        this.searchTools = searchTools;

        // The builder includes the Chat Memory advisor to remember previous questions in the session
        this.chatClient = chatClientBuilder
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            )
            .defaultTools(searchTools)
            .build();
    }

    public String ingestFiles(List<MultipartFile> files) {
        int totalChunks = 0;
        try {
            for (MultipartFile file : files) {
                TikaDocumentReader reader = new TikaDocumentReader(
                    new ByteArrayResource(file.getBytes())
                );
                List<Document> docs = reader.get();

                docs.forEach(d ->
                    d.getMetadata().put("source", file.getOriginalFilename())
                );

                TokenTextSplitter splitter = new TokenTextSplitter();
                List<Document> splitDocs = splitter.apply(docs);
                vectorStore.add(splitDocs);
                totalChunks += splitDocs.size();
            }
            return (
                "Successfully processed " +
                files.size() +
                " files into " +
                totalChunks +
                " chunks."
            );
        } catch (Exception e) {
            return "Failed to ingest files: " + e.getMessage();
        }
    }

    public Flux<String> generateAnswer(String query) {
        // 1. Search Neo4j for local context
        List<Document> similarDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.7)
                .build()
        );

        String context = similarDocs.isEmpty()
            ? ""
            : similarDocs
                  .stream()
                  .map(Document::getText)
                  .collect(Collectors.joining("\n\n"));

        // 2. Build a high-quality System Prompt to fix the "clustered" text
        String systemInstruction = """
            You are Shreyas's Personal AI Workstation Assistant.

            FORMATTING RULES:
            - Use double line breaks between paragraphs for readability.
            - Use **Bold Headers** for steps or categories.
            - Use Markdown code blocks (```bash) for Linux commands.
            - Keep your tone professional, helpful, and concise.

            KNOWLEDGE GUIDELINES:
            - If context is provided below, prioritize it.
            - If you don't know the answer and there is no context, admit it,
              but offer to help search or explain related concepts.
            """;

        String finalSystemMessage =
            systemInstruction +
            (context.isEmpty()
                ? "\n\n(No local documents found for this query. Use your general knowledge.)"
                : "\n\nUse this local document context to answer:\n" + context);

        // 3. Stream the response back to the UI
        return chatClient
            .prompt()
            .system(finalSystemMessage)
            .user(query)
            // .tools(searchTools)
            .stream()
            .content();
    }
}
