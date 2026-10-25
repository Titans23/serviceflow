package com.serviceflow.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeChunkerTest {
    @Test
    void keepsDifferentFaqEvidenceSeparateAndPreservesApplicability() {
        var chunks = KnowledgeChunker.split("# Mate X6\n适用：标准版\n\n## storage：存储\n不支持存储卡\n\n## usb：线材\n标配线为 USB 2.0\n");
        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst()).contains("适用：标准版", "不支持存储卡").doesNotContain("标配线");
        assertThat(chunks.getLast()).contains("适用：标准版", "USB 2.0");
    }

    @Test
    void windowsLongSectionsWithoutDroppingTheirTail() {
        var chunks = KnowledgeChunker.split("# 文档\n\n## 长文\n" + "甲".repeat(5_000) + "结尾标记");
        assertThat(chunks).allMatch(s -> s.length() <= 2_000);
        assertThat(chunks.getLast()).endsWith("结尾标记");
        assertThat(chunks.size()).isGreaterThan(1);
    }

    @Test
    void handlesPlainTextAndEmptyDocuments() {
        assertThat(KnowledgeChunker.split(" ")).isEmpty();
        assertThat(KnowledgeChunker.split("普通 PDF 文本")).containsExactly("普通 PDF 文本");
    }
}
