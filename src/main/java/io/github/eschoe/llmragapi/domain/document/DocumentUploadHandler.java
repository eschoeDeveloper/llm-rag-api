package io.github.eschoe.llmragapi.domain.document;

import io.github.eschoe.llmragapi.global.DetailedErrorResponse;
import io.github.eschoe.llmragapi.service.RateLimitingService;
import io.github.eschoe.llmragapi.util.SessionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
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
        return request.multipartData()
                .flatMap(parts -> {
                    FilePart filePart = (FilePart) parts.toSingleValueMap().get("file");
                    Object metadataPart = parts.toSingleValueMap().get("metadata");
                    String metadataJson = metadataPart != null ? metadataPart.toString() : null;
                    
                    if (filePart == null) {
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "MISSING_FILE",
                                        "파일이 제공되지 않았습니다.",
                                        "multipart/form-data에서 'file' 필드가 필요합니다.",
                                        sessionId
                                ));
                    }
                    
                    // 메타데이터 파싱
                    DocumentUploadRequest uploadRequest;
                    try {
                        if (metadataJson != null && !metadataJson.trim().isEmpty()) {
                            uploadRequest = objectMapper.readValue(metadataJson, DocumentUploadRequest.class);
                        } else {
                            uploadRequest = new DocumentUploadRequest();
                        }
                    } catch (Exception e) {
                        uploadRequest = new DocumentUploadRequest();
                    }
                    
                    final DocumentUploadRequest finalUploadRequest = uploadRequest;
                    finalUploadRequest.setSessionId(sessionId);
                    
                    // 파일 크기 체크 (10MB 제한)
                    return filePart.content()
                            .collectList()
                            .map(dataBuffers -> {
                                int totalSize = dataBuffers.stream()
                                        .mapToInt(buffer -> buffer.readableByteCount())
                                        .sum();
                                
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
                            .flatMap(fileContent -> uploadService.uploadDocument(fileContent, filePart.filename(), finalUploadRequest))
                            .flatMap(response -> ServerResponse.ok()
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .header("X-Session-ID", sessionId)
                                    .bodyValue(response));
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
