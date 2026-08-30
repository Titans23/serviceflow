package com.serviceflow.rag;

public interface EmbeddingClient {

    float[] embed(String text);
}
