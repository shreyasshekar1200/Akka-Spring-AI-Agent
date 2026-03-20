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

        this.chatClient = chatClientBuilder
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            )
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
        // 1. Quick intent check — does this query need a live web search?
        String intentCheck = chatClient
            .prompt()
            .system(
                "You are a routing assistant. Reply with only YES or NO. NO means the query can be answered from training knowledge or provided documents. YES means it requires real-time internet data."
            )
            .user(
                "Does this query require real-time internet search to answer accurately? Query: " +
                    query
            )
            .call()
            .content();

        boolean needsSearch =
            intentCheck != null &&
            intentCheck.trim().toUpperCase().startsWith("YES");

        // 2. Conditionally call web search
        String webContext = "";
        if (needsSearch) {
            System.out.println(">>> WEB SEARCH TRIGGERED FOR: " + query);
            String searchResult = searchTools.searchWeb(query);

            if ("NETWORK_UNAVAILABLE".equals(searchResult)) {
                webContext = "NETWORK_UNAVAILABLE";
            } else {
                webContext = searchResult;
            }
        }

        // 3. Search Neo4j for local document context
        List<Document> similarDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.7)
                .build()
        );

        String localContext = similarDocs.isEmpty()
            ? ""
            : similarDocs
                  .stream()
                  .map(Document::getText)
                  .collect(Collectors.joining("\n\n"));

        // 4. Build system prompt
        String systemInstruction = """
            You are Shreyas's Personal AI Workstation Assistant.

            FORMATTING RULES:
            - Use double line breaks between paragraphs for readability.
            - Use **Bold Headers** for steps or categories.
            - Use Markdown code blocks (```bash) for Linux commands.
            - Keep your tone professional, helpful, and concise.

            KNOWLEDGE GUIDELINES:
            - Prioritize provided context over your training data.
            - If no context is provided, answer from your training knowledge.
            """;

        String finalSystemMessage =
            systemInstruction +
            (localContext.isEmpty()
                ? ""
                : "\n\n**Local Documents:**\n" + localContext) +
            (webContext.equals("NETWORK_UNAVAILABLE")
                ? "\n\n**Note:** This query requires internet access but the system is currently offline. Inform the user clearly that you cannot answer this question without an internet connection."
                : webContext.isEmpty()
                    ? ""
                    : "\n\n**Live Web Search Results:**\n" + webContext);

        // 5. Final answer
        String answer = chatClient
            .prompt()
            .system(finalSystemMessage)
            .user(query)
            .call()
            .content();

        return Flux.just(
            answer != null ? answer : "I'm sorry, I couldn't find an answer."
        );
    }
}
