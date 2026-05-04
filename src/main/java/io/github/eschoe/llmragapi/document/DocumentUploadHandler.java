package io.github.eschoe.llmragapi.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eschoe.llmragapi.common.exception.DetailedErrorResponse;
import io.github.eschoe.llmragapi.common.helper.SessionUtil;
import io.github.eschoe.llmragapi.ratelimit.RateLimitingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DocumentUploadHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadHandler.class);

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

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
        final String sessionId = sessionUtil.extractSessionId(request);
        log.info("[upload] request received, session={}", sessionId);

        return rateLimitingService.isAllowed(sessionId)
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("[upload] rate limit exceeded, session={}", sessionId);
                        return ServerResponse.status(429)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "RATE_LIMIT_EXCEEDED",
                                        "요청 한도를 초과했습니다.",
                                        "문서 업로드 요청이 너무 많습니다.",
                                        sessionId
                                ));
                    }
                    return processDocumentUpload(request, sessionId);
                })
                .onErrorResume(e -> handleError(e, sessionId));
    }

    private Mono<ServerResponse> processDocumentUpload(ServerRequest request, String sessionId) {
        return request.multipartData()
                .flatMap(parts -> {
                    FilePart filePart = (FilePart) parts.toSingleValueMap().get("file");
                    Part metadataPart = parts.toSingleValueMap().get("metadata");

                    if (filePart == null) {
                        log.warn("[upload] file part missing, session={}", sessionId);
                        return ServerResponse.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new DetailedErrorResponse(
                                        "MISSING_FILE",
                                        "파일이 제공되지 않았습니다.",
                                        "multipart/form-data 에서 'file' 필드가 필요합니다.",
                                        sessionId
                                ));
                    }

                    return parseMetadata(metadataPart)
                            .flatMap(uploadRequest -> {
                                uploadRequest.setSessionId(sessionId);
                                return readFileContent(filePart)
                                        .flatMap(fileContent -> uploadService
                                                .uploadDocument(fileContent, filePart.filename(), uploadRequest))
                                        .flatMap(response -> respondByStatus(response, sessionId));
                            });
                });
    }

    private Mono<DocumentUploadRequest> parseMetadata(Part metadataPart) {
        if (metadataPart == null) {
            return Mono.just(defaultMetadata());
        }
        return metadataPart.content()
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .reduce("", (s1, s2) -> s1 + s2)
                .map(json -> {
                    try {
                        if (!json.isBlank()) {
                            return objectMapper.readValue(json, DocumentUploadRequest.class);
                        }
                    } catch (Exception e) {
                        log.warn("[upload] metadata parse failed, falling back: {}", e.getMessage());
                    }
                    return defaultMetadata();
                });
    }

    private Mono<byte[]> readFileContent(FilePart filePart) {
        return filePart.content()
                .collectList()
                .map(this::concatBuffers);
    }

    private byte[] concatBuffers(List<DataBuffer> buffers) {
        long totalSize = buffers.stream().mapToLong(DataBuffer::readableByteCount).sum();
        if (totalSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "파일 크기가 너무 큽니다. 최대 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB까지 업로드 가능합니다.");
        }
        byte[] data = new byte[(int) totalSize];
        int offset = 0;
        for (DataBuffer buffer : buffers) {
            int len = buffer.readableByteCount();
            buffer.read(data, offset, len);
            offset += len;
        }
        return data;
    }

    private Mono<ServerResponse> respondByStatus(DocumentUploadResponse response, String sessionId) {
        String status = response.getStatus();
        boolean failed = "FAILED".equalsIgnoreCase(status);

        if (failed) {
            log.warn("[upload] document upload FAILED: id={}, errors={}",
                    response.getDocumentId(), response.getErrors());
            // 422 Unprocessable Entity — 요청은 받았지만 콘텐츠 처리 실패
            return ServerResponse.status(422)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Session-ID", sessionId)
                    .bodyValue(response);
        }

        log.info("[upload] document upload OK: id={}, chunks={}/{}",
                response.getDocumentId(), response.getProcessedChunks(), response.getTotalChunks());
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Session-ID", sessionId)
                .bodyValue(response);
    }

    private DocumentUploadRequest defaultMetadata() {
        DocumentUploadRequest req = new DocumentUploadRequest();
        req.setTitle("Untitled Document");
        req.setDescription("");
        req.setCategory("");
        return req;
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
        String details = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

        if (e instanceof IllegalArgumentException) {
            errorType = "INVALID_INPUT";
            userMessage = details;
        } else if (details.contains("크기가")) {
            errorType = "FILE_TOO_LARGE";
            userMessage = "파일 크기가 너무 큽니다.";
        } else if (details.contains("형식")) {
            errorType = "INVALID_FILE_FORMAT";
            userMessage = "지원되지 않는 파일 형식입니다.";
        } else if (details.toLowerCase().contains("timeout")) {
            errorType = "UPLOAD_TIMEOUT";
            userMessage = "업로드 시간이 초과되었습니다.";
        }

        log.error("[upload] error: {}", details, e);

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
