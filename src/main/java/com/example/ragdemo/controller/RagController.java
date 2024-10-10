package com.example.ragdemo.controller;

import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Media;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class RagController {
    private final ChatClient client;
    private final ChatModel chatModel;
    private final VectorStore db;

    public RagController(ChatClient.Builder builder, ChatModel chatModel, VectorStore db) {
        this.client = builder.build();
        this.chatModel = chatModel;
        this.db = db;
    }

    @GetMapping
    public String describe() {
        return """
                This is an application to populate and query a vector store, effectively turning loose 
                an AI on your data. This is a potentially powerful, focused tool, so as always, *verify your results*.
                
                To populate the vector store with embeddings for a supplied document, simply provide a 
                file path or URL that resolves to the document to be processed:
                
                /populate?filepath=<path or URL>
                
                To query the vector store for documents/data that matches your query, use the following endpoint:
                
                /rag?message=<your query>
                
                DISCLAIMER: No warranty is provided or implied. Use at your own risk. :)
                """;
    }

    // Baseline endpoint, non-RAG queries
    @GetMapping("/query")
    public String query(@RequestParam String message) {
        return client
                .prompt()
                .user(message)
                .call()
                .content();
    }

    // http://localhost:8081/populate?filepath=D:/bootcamp2/ragdemo/aaa.pdf
  @GetMapping("/populate")
    public String populate(@RequestParam String filepath) throws MalformedURLException {

        var logger = LoggerFactory.getLogger(RagController.class);

        var importFile = filepath.startsWith("http")
                ? new UrlResource(filepath)
                : new FileSystemResource(filepath);
        var tikaDocumentReader = new TikaDocumentReader(importFile);
        var splitter = new TokenTextSplitter();

        logger.info("Populating vector store with " + filepath);

        db.add(splitter.apply(tikaDocumentReader.get()));

        logger.info("Vector store population complete!");

        return "Populated vector store with " + filepath;
    }

    // http://localhost:8081/rag?message=인공지능의 발전
    @GetMapping("/rag")
    public String getRagResponse(@RequestParam(defaultValue = "Airspeeds") String message) {
        return client.prompt()
                .user(message)
                .advisors(new QuestionAnswerAdvisor(db, SearchRequest.defaults()))
                .call()
                .content();
    }

    @GetMapping("/mm")
    public String getMultimodalResponse(
            @RequestParam(defaultValue = "/Users/markheckler/files/testimage.jpg") String imagePath,
            @RequestParam(defaultValue = "이 이미지에 무엇이 있나요?") String message) throws MalformedURLException {

        var imageType = imagePath.endsWith(".png") ? MimeTypeUtils.IMAGE_PNG : MimeTypeUtils.IMAGE_JPEG;
        var media = imagePath.startsWith("http")
                ? new Media(imageType, new URL(imagePath))
                : new Media(imageType, new FileSystemResource(imagePath));

        var userMessage = new UserMessage(message, media);
        var systemMessage = new SystemMessage("이미지를 확실히 식별할 수 없다면 최선의 추측을 해보세요.");

        return chatModel.call(userMessage, systemMessage);
    }

    @GetMapping("/imagerag")
    public String getImageRagResponse(
            @RequestParam(defaultValue = "/Users/markheckler/files/testimage.jpg") String imagePath,
            @RequestParam(defaultValue = "이 이미지에 대해 모든 것을 알려주세요") String message) throws MalformedURLException {

        // 먼저 AI를 사용하여 이미지를 분석한 후, RAG를 사용해 도메인 관련 문서에서 정보를 검색합니다.
        return getRagResponse(getMultimodalResponse(imagePath, message));
    }

    @GetMapping("/ragtest")
    public String getRagTest(String query){
        List<Document> documents = List.of(
                new Document("Spring AI 최고다!! Spring AI 최고다!! Spring AI 최고다!! Spring AI 최고다!! Spring AI 최고다!!", Map.of("meta1", "meta1")),
                new Document("세상은 크고 구원은 코너 뒤에 숨어있다."),
                new Document("당신은 과거를 향해 걸어가고 미래를 향해 뒤돌아본다.", Map.of("meta2", "meta2"))
        );

        // 문서를 벡터 스토어에 추가
        db.add(documents);

        // 쿼리와 유사한 문서를 검색
        List<Document> results = db.similaritySearch(SearchRequest.query(query).withTopK(5));

        // StringBuilder를 사용하여 결과 내용을 저장
        StringBuilder resultContent = new StringBuilder();

        for (Document doc : results) {
            resultContent.append("문서 내용: ").append(doc.getContent()).append("\n");
            // 메타데이터 출력 (선택 사항)
            if (doc.getMetadata() != null) {
                resultContent.append("메타데이터: ").append(doc.getMetadata().toString()).append("\n");
            }
            resultContent.append("\n"); // 문서 간에 구분을 위해 빈 줄 추가
        }

        // 결과 내용을 반환
        return resultContent.toString();
    }
}