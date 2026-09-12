package com.serviceflow.rag;

public interface EmbeddingClient {

    /**
     * 将一段可变长度文本转换为固定维度的语义向量。
     * 文档入库和查询检索必须使用兼容的模型，否则两类向量无法正确比较。
     */
    float[] embed(String text);
}
