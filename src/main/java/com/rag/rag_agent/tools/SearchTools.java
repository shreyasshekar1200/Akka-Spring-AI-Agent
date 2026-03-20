package com.rag.rag_agent.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class SearchTools {

    @Tool(
        description = "Search the internet for real-time information, news, or facts not in local files."
    )
    public String searchWeb(String query) {
        System.out.println(">>> SEARCH TOOL CALLED WITH: " + query);
        try {
            RestTemplate rest = new RestTemplate();
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = "https://html.duckduckgo.com/html/";

            HttpHeaders headers = new HttpHeaders();
            headers.set(
                "User-Agent",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"
            );
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> entity = new HttpEntity<>(
                "q=" + encodedQuery,
                headers
            );

            ResponseEntity<String> response = rest.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
            );

            String html = response.getBody();

            // Try primary snippet class
            Pattern pattern = Pattern.compile(
                "class=\"result__snippet\"[^>]*>(.*?)</a>",
                Pattern.DOTALL
            );
            Matcher matcher = pattern.matcher(html);

            // Fallback to lite HTML snippet class
            if (!matcher.find()) {
                pattern = Pattern.compile(
                    "<td class=\"result-snippet\">(.*?)</td>",
                    Pattern.DOTALL
                );
                matcher = pattern.matcher(html);
            } else {
                matcher.reset();
            }

            StringBuilder results = new StringBuilder();
            int count = 0;
            while (matcher.find() && count < 5) {
                String snippet = matcher
                    .group(1)
                    .replaceAll("<[^>]+>", "")
                    .trim();
                if (!snippet.isEmpty()) {
                    results.append(snippet).append("\n\n");
                    count++;
                }
            }

            String extracted = results.toString().trim();

            if (extracted.isEmpty()) {
                System.out.println(">>> NO SNIPPETS EXTRACTED FROM HTML");
                return "No results found for: " + query;
            }

            System.out.println(
                ">>> SEARCH RESULT RECEIVED: " + count + " snippets"
            );
            return extracted;
        } catch (ResourceAccessException e) {
            System.out.println(">>> NETWORK UNAVAILABLE: " + e.getMessage());
            return "NETWORK_UNAVAILABLE";
        } catch (Exception e) {
            System.out.println(">>> SEARCH FAILED: " + e.getMessage());
            return "NETWORK_UNAVAILABLE";
        }
    }
}
