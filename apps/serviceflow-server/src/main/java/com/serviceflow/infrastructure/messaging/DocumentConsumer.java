package com.serviceflow.infrastructure.messaging;

import com.serviceflow.config.RabbitConfig;
import com.serviceflow.mapper.KnowledgeMapper;
import com.serviceflow.model.KnowledgeModels;
import com.serviceflow.rag.RagService;
import com.serviceflow.service.KnowledgeService;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public final class DocumentConsumer {

    private static final int CHUNK_CHARS = 2_000;
    private static final int OVERLAP_CHARS = 320;
    private static final int MAX_PARSED_CHARS = 2_000_000;
    private static final int MAX_ERROR_LENGTH = 500;

    private final KnowledgeMapper mapper;
    private final RagService rag;
    private final KnowledgeService service;
    private final Tika tika = new Tika();

    public DocumentConsumer(KnowledgeMapper mapper, RagService rag, KnowledgeService service) {
        this.mapper = mapper;
        this.rag = rag;
        this.service = service;
    }

    @RabbitListener(queues = RabbitConfig.DOCUMENT_QUEUE)
    public void consume(long versionId) {
        KnowledgeModels.Version version = mapper.findVersionById(versionId);
        if (version == null || "READY".equals(version.status())) {
            return;
        }
        if (mapper.markProcessing(versionId) != 1) {
            return;
        }

        try (InputStream input = Files.newInputStream(Path.of(version.storagePath()))) {
            String text = tika.parseToString(input);
            if (text.length() > MAX_PARSED_CHARS) {
                throw new IllegalArgumentException("文档解析文本超过 2,000,000 字符");
            }
            rag.deleteVersion(versionId);
            ingestChunks(version, text);
            service.activate(version);
        } catch (Exception exception) {
            mapper.markFailed(versionId, truncate(exception.getMessage()));
            throw new IllegalStateException("Document ingestion failed: " + versionId, exception);
        }
    }

    private void ingestChunks(KnowledgeModels.Version version, String text) {
        int step = CHUNK_CHARS - OVERLAP_CHARS;
        for (int start = 0, index = 0; start < text.length(); start += step, index++) {
            int end = Math.min(text.length(), start + CHUNK_CHARS);
            String content = text.substring(start, end).trim();
            if (!content.isBlank()) {
                rag.ingest(new RagService.Chunk(
                        UUID.randomUUID().toString(),
                        version.documentId(),
                        version.id(),
                        version.documentType(),
                        version.productId(),
                        version.title(),
                        version.documentType(),
                        version.sourceName(),
                        content));
            }
            if (end == text.length()) {
                break;
            }
        }
    }

    private String truncate(String value) {
        String message = value == null ? "未知错误" : value;
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }
}
