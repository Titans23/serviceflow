package com.serviceflow.infrastructure.messaging;

import com.serviceflow.config.RabbitConfig;
import com.serviceflow.mapper.KnowledgeMapper;
import com.serviceflow.model.KnowledgeModels;
import com.serviceflow.rag.RagService;
import com.serviceflow.service.KnowledgeService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public final class DocumentConsumer {

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

    /**
     * 消费文档版本消息：取得处理权，解析文件，重建该版本的向量数据，成功后激活版本。
     */
    @RabbitListener(queues = RabbitConfig.DOCUMENT_QUEUE)
    public void consume(long versionId) {
        KnowledgeModels.Version version = mapper.findVersionById(versionId);
        // 消息可能重复投递；不存在或已经成功的版本无需再次处理。
        if (version == null || "READY".equals(version.status())) {
            return;
        }
        // 只有成功把 PENDING 改成 PROCESSING 的消费者获得本次处理权。
        if (mapper.markProcessing(versionId) != 1) {
            return;
        }

        try (InputStream input = Files.newInputStream(Path.of(version.storagePath()))) {
            // Tika 屏蔽 PDF、Word、Markdown 等文件格式差异，统一提取为纯文本。
            String text =
                    version.sourceName().toLowerCase(java.util.Locale.ROOT).endsWith(".md")
                            ? new String(input.readAllBytes(), StandardCharsets.UTF_8)
                            : tika.parseToString(input);
            if (text.length() > MAX_PARSED_CHARS) {
                throw new IllegalArgumentException("文档解析文本超过 2,000,000 字符");
            }
            // 重试时先删除该版本可能残留的旧 Chunk，避免重复向量。
            rag.deleteVersion(versionId);
            ingestChunks(version, text);
            // 所有 Chunk 入库成功后才将版本标记为 READY 并设为当前生效版本。
            service.activate(version);
        } catch (Exception exception) {
            mapper.markFailed(versionId, truncate(exception.getMessage()));
            throw new IllegalStateException("Document ingestion failed: " + versionId, exception);
        }
    }

    /**
     * FAQ 按二级标题切分；长段落保留重叠窗口。重试相同版本生成相同 Chunk ID。
     */
    private void ingestChunks(KnowledgeModels.Version version, String text) {
        int index = 0;
        for (String content : KnowledgeChunker.split(text)) {
            rag.ingest(new RagService.Chunk(
                    UUID.nameUUIDFromBytes(
                                    (version.id() + ":" + index++ + ":" + content).getBytes(StandardCharsets.UTF_8))
                            .toString(),
                    version.documentId(),
                    version.id(),
                    version.documentType(),
                    version.productId(),
                    version.title(),
                    version.documentType(),
                    version.sourceName(),
                    content));
        }
    }

    private String truncate(String value) {
        String message = value == null ? "未知错误" : value;
        return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
    }
}
