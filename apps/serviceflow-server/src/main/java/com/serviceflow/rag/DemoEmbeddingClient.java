package com.serviceflow.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "serviceflow.rag.mode", havingValue = "demo", matchIfMissing = true)
public final class DemoEmbeddingClient implements EmbeddingClient {
    private static final int DIMENSIONS = 1024;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding text must not be blank");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            float[] vector = new float[DIMENSIONS];
            for (int index = 0; index < vector.length; index++) {
                int value = digest[index % digest.length] & 0xff;
                vector[index] = (value - 128) / 128.0f;
            }
            return vector;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
