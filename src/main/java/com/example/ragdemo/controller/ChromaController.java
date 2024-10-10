package com.example.ragdemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class ChromaController {

    private static final Logger logger = LoggerFactory.getLogger(ChromaController.class);

    private final WebClient webClient = WebClient.create();  // WebClient instance

    @GetMapping("/fetchCollections")
    public String fetchCollections() {
        // Fetch data from Chroma server
        String response = webClient.get()
            .uri("http://localhost:8000/api/v1/collections")
            .retrieve()
            .bodyToMono(String.class)
            .block();

        // Log the response
        logger.info("Chroma Collections Response: " + response);

        // Return the response
        return response;
    }
}
