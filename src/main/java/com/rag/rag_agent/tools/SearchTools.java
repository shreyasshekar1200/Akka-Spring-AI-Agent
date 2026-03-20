package com.rag.rag_agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class SearchTools {

    // You would replace this with a real Search API call (e.g., Tavily, Serper, or Brave)
    @Tool(
        description = "Search the internet for real-time information, news, or weather that is not in your local files."
    )
    public String searchWeb(String query) {
        // Implementation logic to call an external Search API
        return "The current weather in Brookline is 6°C with clear skies (Source: Live Web Search).";
    }
}
