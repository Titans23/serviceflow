package com.serviceflow.knowledge;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.config.ServiceFlowProperties;
import com.serviceflow.product.ProductService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("md", "txt", "pdf");
    private static final Set<String> SUPPORTED_TYPES = Set.of("POLICY", "PRODUCT_MANUAL", "CUSTOMER_SERVICE_SOP");

    private final KnowledgeMapper mapper;
    private final ProductService products;
    private final ObjectMapper objectMapper;
    private final Path uploadRoot;
    private final Tika tika = new Tika();

    public KnowledgeService(
            KnowledgeMapper mapper,
            ServiceFlowProperties properties,
            ProductService products,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.products = products;
        this.objectMapper = objectMapper;
        this.uploadRoot = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
    }

    @Transactional
    public KnowledgeMapper.VersionRow create(String title, String type, Long productId, MultipartFile file) {
        validateMetadata(title, type, productId);
        String publicId = UUID.randomUUID().toString();
        mapper.insertDocument(publicId, title.trim(), type, productId);
        return addVersion(publicId, file);
    }

    @Transactional
    public KnowledgeMapper.VersionRow addVersion(String publicId, MultipartFile file) {
        KnowledgeMapper.DocumentRow document = mapper.findDocument(publicId);
        if (document == null) {
            throw new ResponseStatusException(NOT_FOUND, "知识文档不存在");
        }

        ValidFile validFile = validateFile(file);
        int version = mapper.nextVersion(document.id());
        Path target = uploadRoot
                .resolve(publicId)
                .resolve("v" + version + '.' + validFile.extension())
                .normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new ResponseStatusException(BAD_REQUEST, "非法文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (Exception exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "文件保存失败", exception);
        }

        try {
            mapper.insertVersion(document.id(), version, validFile.originalName(), target.toString());
        } catch (Exception exception) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "文档版本保存失败", exception);
        }
        KnowledgeMapper.VersionRow row = mapper.findVersion(document.id(), version);
        mapper.enqueueOutbox(row.id(), outboxPayload(row.id()));
        return row;
    }

    public List<KnowledgeMapper.DocumentView> list() {
        return mapper.list();
    }

    @Transactional
    public void retry(long versionId) {
        KnowledgeMapper.VersionRow row = mapper.findVersionById(versionId);
        if (row == null) {
            throw new ResponseStatusException(NOT_FOUND, "文档版本不存在");
        }
        if (mapper.markPending(versionId) != 1) {
            throw new ResponseStatusException(CONFLICT, "只有失败版本可以重试");
        }
        mapper.requeueOutbox(versionId, outboxPayload(versionId));
    }

    @Transactional
    public void activate(KnowledgeMapper.VersionRow version) {
        mapper.markReady(version.id());
        mapper.activate(version.documentId(), version.id());
    }

    private void validateMetadata(String title, String type, Long productId) {
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new ResponseStatusException(BAD_REQUEST, "标题长度应为 1 至 200");
        }
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new ResponseStatusException(BAD_REQUEST, "不支持的文档类型");
        }
        if ("PRODUCT_MANUAL".equals(type) && productId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "商品说明书必须关联商品");
        }
        if ("PRODUCT_MANUAL".equals(type)) {
            products.get(productId);
        }
    }

    private ValidFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "文件不能超过 20 MB");
        }

        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("document");
        String extension = extensionOf(originalName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(UNSUPPORTED_MEDIA_TYPE, "仅支持 MD、TXT、PDF");
        }

        try {
            String mime = tika.detect(file.getInputStream(), originalName);
            if (!(mime.startsWith("text/") || "application/pdf".equals(mime))) {
                throw new ResponseStatusException(UNSUPPORTED_MEDIA_TYPE, "文件内容类型不受支持");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_REQUEST, "无法识别文件类型", exception);
        }
        return new ValidFile(Path.of(originalName).getFileName().toString(), extension);
    }

    private String extensionOf(String filename) {
        int separator = filename.lastIndexOf('.');
        return separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String outboxPayload(long versionId) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("documentVersionId", versionId));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize knowledge outbox event", exception);
        }
    }

    private record ValidFile(String originalName, String extension) {}
}
