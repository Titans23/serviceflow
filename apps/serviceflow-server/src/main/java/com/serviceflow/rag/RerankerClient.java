package com.serviceflow.rag;

import java.util.List;

public interface RerankerClient {

    /**
     * 根据用户问题重新判断候选分块的相关性，返回相关性最高的分块 ID，顺序即新排名。
     * 这里不负责首次召回；输入 documents 已经是混合检索筛出的候选集合。
     */
    List<String> rank(String query, List<Document> documents, int topN);

    record Document(String id, String text) {}
}
