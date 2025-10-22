package io.github.eschoe.llmragapi.domain.document;

import io.github.eschoe.llmragapi.global.DetailedErrorResponse;
import io.github.eschoe.llmragapi.service.RateLimitingService;
import io.github.eschoe.llmragapi.util.SessionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.stream.Collectors;

@Component
public class DocumentUploadHandler {

    private final DocumentUploadService uploadService;
    private final ObjectMapper objectMapper;
    private final SessionUtil sessionUtil;
    private final RateLimitingService rateLimitingService;

    public DocumentUploadHandler(DocumentUploadService uploadService,
                               ObjectMapper objectMapper,
                               SessionUtil sessionUtil,
                               RateLimitingService rateLimitingService) {
        this.uploadService = uploadService;
        this.objectMapper = objectMapper;
        this.sessionUtil = sessionUtil;
        this.rateLimitingService = rateLimitingService;
    }

    public Mono<ServerResponse> uploadDocument(ServerRequest request) {
        System.out.println("[DocumentUploadHandler] Upload request received");
        final String sessionId = sessionUtil.extractSessionId(request);
        System.out.println("[DocumentUploadHandler] Session ID: " + sessionId);
        
        // Rate Limiting 체크
        return rateLimitingService.isAllowed(sessionId)
                .doOnNext(allowed -> System.out.println("[DocumentUploadHandler] Rate limit check: " + allowed))
                .flatMap(allowed -> {
                    if (!allowed) {
                        System.out.println("[DocumentUploadHandler] Rate limit exceeded");
                        return ServerResponse.status(429)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "RATE_LIMIT_EXCEEDED",
                                        "요청 한도를 초과했습니다.",
                                        "문서 업로드 요청이 너무 많습니다.",
                                        sessionId
                                ));
                    }
                    
                    System.out.println("[DocumentUploadHandler] Processing document upload");
                    return processDocumentUpload(request, sessionId);
                })
                .doOnSuccess(response -> System.out.println("[DocumentUploadHandler] Upload successful"))
                .doOnError(e -> System.err.println("[DocumentUploadHandler] Upload error: " + e.getMessage()))
                .onErrorResume(e -> handleError(e, sessionId));
    }

    private Mono<ServerResponse> processDocumentUpload(ServerRequest request, String sessionId) {
        System.out.println("[DocumentUploadHandler] processDocumentUpload called");
        return request.multipartData()
                .doOnNext(parts -> System.out.println("[DocumentUploadHandler] Multipart data received, parts: " + parts.toSingleValueMap().keySet()))
                .flatMap(parts -> {
                    FilePart filePart = (FilePart) parts.toSingleValueMap().get("file");
                    Part metadataPart = parts.toSingleValueMap().get("metadata");
                    
                    System.out.println("[DocumentUploadHandler] File part: " + (filePart != null ? filePart.filename() : "null"));
                    System.out.println("[DocumentUploadHandler] Metadata part: " + metadataPart);
                    
                    if (filePart == null) {
                        System.err.println("[DocumentUploadHandler] File part is null!");
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "MISSING_FILE",
                                        "파일이 제공되지 않았습니다.",
                                        "multipart/form-data에서 'file' 필드가 필요합니다.",
                                        sessionId
                                ));
                    }
                    
                    // 메타데이터 파싱 (FormFieldPart에서 실제 content 읽기)
                    Mono<DocumentUploadRequest> metadataMono;
                    if (metadataPart != null) {
                        metadataMono = metadataPart.content()
                                .map(dataBuffer -> {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                                })
                                .reduce("", (s1, s2) -> s1 + s2)
                                .map(metadataJson -> {
                                    System.out.println("[DocumentUploadHandler] Metadata JSON: " + metadataJson);
                                    try {
                                        if (metadataJson != null && !metadataJson.trim().isEmpty()) {
                                            return objectMapper.readValue(metadataJson, DocumentUploadRequest.class);
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[DocumentUploadHandler] Metadata parsing error: " + e.getMessage());
                                    }
                                    // 기본값 설정
                                    DocumentUploadRequest defaultRequest = new DocumentUploadRequest();
                                    defaultRequest.setTitle("Untitled Document");
                                    defaultRequest.setDescription("");
                                    defaultRequest.setCategory("");
                                    return defaultRequest;
                                });
                    } else {
                        // 메타데이터가 없는 경우 기본값
                        DocumentUploadRequest defaultRequest = new DocumentUploadRequest();
                        defaultRequest.setTitle("Untitled Document");
                        defaultRequest.setDescription("");
                        defaultRequest.setCategory("");
                        metadataMono = Mono.just(defaultRequest);
                    }
                    
                    return metadataMono.flatMap(uploadRequest -> {
                        final DocumentUploadRequest finalUploadRequest = uploadRequest;
                        finalUploadRequest.setSessionId(sessionId);
                        
                        System.out.println("[DocumentUploadHandler] Reading file content...");
                        // 파일 크기 체크 (10MB 제한)
                        return filePart.content()
                                .collectList()
                                .doOnNext(buffers -> System.out.println("[DocumentUploadHandler] Collected " + buffers.size() + " buffers"))
                                .map(dataBuffers -> {
                                    int totalSize = dataBuffers.stream()
                                            .mapToInt(buffer -> buffer.readableByteCount())
                                            .sum();
                                    
                                    System.out.println("[DocumentUploadHandler] Total file size: " + totalSize + " bytes");
                                    
                                    if (totalSize > 10 * 1024 * 1024) { // 10MB
                                        throw new RuntimeException("파일 크기가 너무 큽니다. 최대 10MB까지 업로드 가능합니다.");
                                    }
                                    
                                    byte[] fileContent = new byte[totalSize];
                                    int offset = 0;
                                    for (org.springframework.core.io.buffer.DataBuffer buffer : dataBuffers) {
                                        buffer.read(fileContent, offset, buffer.readableByteCount());
                                        offset += buffer.readableByteCount();
                                    }
                                    
                                    return fileContent;
                                })
                                .doOnNext(content -> System.out.println("[DocumentUploadHandler] File content ready, calling uploadService"))
                                .flatMap(fileContent -> uploadService.uploadDocument(fileContent, filePart.filename(), finalUploadRequest))
                                .doOnNext(response -> System.out.println("[DocumentUploadHandler] Upload service returned: " + response))
                                .flatMap(response -> {
                                    System.out.println("[DocumentUploadHandler] Creating response with: " + response);
                                    return ServerResponse.ok()
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .header("X-Session-ID", sessionId)
                                            .bodyValue(response);
                                })
                                .doOnSuccess(serverResponse -> System.out.println("[DocumentUploadHandler] ServerResponse created successfully"))
                                .doOnError(e -> System.err.println("[DocumentUploadHandler] Error in processDocumentUpload: " + e.getMessage()));
                    });
                });
    }

    public Mono<ServerResponse> getUserDocuments(ServerRequest request) {
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return uploadService.getUserDocuments(sessionId)
                .flatMap(documents -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue(documents));
    }

    public Mono<ServerResponse> getDocument(ServerRequest request) {
        String documentId = request.pathVariable("documentId");
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return uploadService.getDocument(documentId)
                .flatMap(document -> {
                    if (document == null) {
                        return ServerResponse.status(404)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "DOCUMENT_NOT_FOUND",
                                        "문서를 찾을 수 없습니다.",
                                        "Document ID: " + documentId,
                                        sessionId
                                ));
                    }
                    
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Session-ID", sessionId)
                            .bodyValue(document);
                });
    }

    public Mono<ServerResponse> deleteDocument(ServerRequest request) {
        String documentId = request.pathVariable("documentId");
        final String sessionId = sessionUtil.extractSessionId(request);
        
        return uploadService.deleteDocument(documentId)
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Session-ID", sessionId)
                        .bodyValue("Document deleted successfully"));
    }

    private Mono<ServerResponse> handleError(Throwable e, String sessionId) {
        String errorType = "UPLOAD_ERROR";
        String userMessage = "문서 업로드 중 오류가 발생했습니다.";
        String details = e.getMessage();
        
        if (e.getMessage().contains("크기가")) {
            errorType = "FILE_TOO_LARGE";
            userMessage = "파일 크기가 너무 큽니다.";
        } else if (e.getMessage().contains("형식")) {
            errorType = "INVALID_FILE_FORMAT";
            userMessage = "지원되지 않는 파일 형식입니다.";
        } else if (e.getMessage().contains("timeout")) {
            errorType = "UPLOAD_TIMEOUT";
            userMessage = "업로드 시간이 초과되었습니다.";
        }
        
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new DetailedErrorResponse(
                        errorType,
                        userMessage,
                        details,
                        sessionId
                ));
    }
}
