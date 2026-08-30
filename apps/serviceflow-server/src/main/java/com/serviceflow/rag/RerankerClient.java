package com.serviceflow.rag;

import java.util.List;

public interface RerankerClient {

    List<String> rank(String query, List<Document> documents, int topN);

    record Document(String id, String text) {}
}
