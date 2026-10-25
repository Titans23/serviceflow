package com.serviceflow.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Opt-in evaluation evidence, written locally; never a customer-facing transcript or document dump. */
@Component
public final class RagTraceRecorder {
    private static final Logger log = LoggerFactory.getLogger(RagTraceRecorder.class);
    private final boolean enabled;
    private final Path directory;
    private final ObjectMapper mapper;

    public RagTraceRecorder(
            @Value("${serviceflow.evaluation.enabled:false}") boolean evaluationEnabled,
            @Value("${serviceflow.evaluation.rag-diagnostics-dir:}") String directory,
            ObjectMapper mapper) {
        this.enabled = evaluationEnabled && !directory.isBlank();
        this.directory = enabled ? Path.of(directory) : null;
        this.mapper = mapper;
    }

    public Trace begin(String query, String documentType, List<Long> productIds) {
        return new Trace(enabled ? UUID.randomUUID().toString() : null, query, documentType, productIds);
    }

    public final class Trace {
        private final String id;
        private final Map<String, Object> data = new LinkedHashMap<>();
        private final List<Map<String, Object>> stages = new ArrayList<>();

        private Trace(String id, String query, String type, List<Long> products) {
            this.id = id;
            if (id != null) {
                data.put("traceId", id);
                data.put("createdAt", Instant.now().toString());
                data.put("originalQuery", query);
                data.put("originalQuerySha256", digest(query));
                data.put("documentType", type);
                data.put("productIds", products == null ? List.of() : products);
                data.put("stages", stages);
            }
        }

        public void versions(List<Long> versions) {
            if (id != null) data.put("activeVersionIds", versions);
        }

        public void stage(
                String name,
                String query,
                List<RagService.Evidence> input,
                List<RagService.Evidence> output,
                String reason,
                boolean sufficient,
                boolean degraded,
                long started) {
            if (id == null) return;
            List<String> selected =
                    output.stream().map(RagService.Evidence::chunkId).toList();
            List<Map<String, Object>> decisions = new ArrayList<>();
            for (int i = 0; i < input.size(); i++) {
                String chunk = input.get(i).chunkId();
                int rank = selected.indexOf(chunk);
                decisions.add(Map.of(
                        "chunkId",
                        chunk,
                        "inputRank",
                        i + 1,
                        "outputRank",
                        rank + 1,
                        "reason",
                        rank >= 0 ? "retained:" + reason : "not_selected:" + reason));
            }
            Map<String, Object> stage = new LinkedHashMap<>(Map.of(
                    "stage",
                    name,
                    "query",
                    query,
                    "querySha256",
                    digest(query),
                    "input",
                    ranks(input),
                    "output",
                    ranks(output),
                    "decisions",
                    decisions,
                    "reason",
                    reason,
                    "degraded",
                    degraded,
                    "elapsedMs",
                    (System.nanoTime() - started) / 1_000_000.0));
            if (name.endsWith(".grader")) stage.put("sufficient", sufficient);
            stages.add(stage);
        }

        public String finish(RagService.SearchResult result, long started) {
            if (id == null) return null;
            data.put(
                    "result",
                    Map.of(
                            "sufficient",
                            result.sufficient(),
                            "degraded",
                            result.degraded(),
                            "evidence",
                            ranks(result.evidence()),
                            "elapsedMs",
                            (System.nanoTime() - started) / 1_000_000.0));
            try {
                Files.createDirectories(directory);
                Files.writeString(
                        directory.resolve(id + ".raw.json"),
                        mapper.writeValueAsString(data),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
                return id;
            } catch (Exception exception) {
                // Diagnostics cannot replace a business result; absence of an ID prevents claiming a complete trace.
                log.warn(
                        "RAG diagnostic write failed ({})", exception.getClass().getSimpleName());
                return null;
            }
        }
    }

    private static List<Map<String, Object>> ranks(List<RagService.Evidence> evidence) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < evidence.size(); i++) {
            result.add(Map.of("chunkId", evidence.get(i).chunkId(), "rank", i + 1));
        }
        return result;
    }

    private static String digest(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
