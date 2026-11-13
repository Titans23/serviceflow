package com.serviceflow.infrastructure.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.serviceflow.mapper.KnowledgeMapper;
import com.serviceflow.model.KnowledgeModels;
import com.serviceflow.rag.RagService;
import com.serviceflow.service.KnowledgeService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentRecoveryTest {
    @TempDir
    Path directory;

    @Test
    void expiredParserCannotWriteOrActivateItsOldVersion() throws Exception {
        var mapper = mock(KnowledgeMapper.class);
        var rag = mock(RagService.class);
        var service = mock(KnowledgeService.class);
        Path file = directory.resolve("test.md");
        Files.writeString(file, "# Test\n\ncontent");
        var version = new KnowledgeModels.Version(
                1, 1, 1, "test.md", file.toString(), "PENDING", null, "Test", "POLICY", null);
        when(mapper.findVersionById(1)).thenReturn(version);
        when(mapper.markProcessing(1)).thenReturn(1);
        when(mapper.renew(1)).thenReturn(0);
        new DocumentConsumer(mapper, rag, service).consume(1);
        verify(service).recover(1);
        verify(service, never()).activate(any());
        verifyNoInteractions(rag);
    }

    @Test
    void retryUsesDistinctVectorNamespaceForIdenticalSource() throws Exception {
        var mapper = mock(KnowledgeMapper.class);
        var rag = mock(RagService.class);
        var service = mock(KnowledgeService.class);
        Path file = directory.resolve("test.md");
        Files.writeString(file, "# Test\n\ncontent");
        for (long id : new long[] {1, 2}) {
            when(mapper.findVersionById(id))
                    .thenReturn(new KnowledgeModels.Version(
                            id, 1, (int) id, "test.md", file.toString(), "PENDING", null, "Test", "POLICY", null));
            when(mapper.markProcessing(id)).thenReturn(1);
            when(mapper.renew(id)).thenReturn(1);
            new DocumentConsumer(mapper, rag, service).consume(id);
        }
        var chunks = org.mockito.ArgumentCaptor.forClass(RagService.Chunk.class);
        verify(rag, times(2)).ingest(chunks.capture());
        assertThat(chunks.getAllValues())
                .extracting(RagService.Chunk::documentVersionId)
                .containsExactly(1L, 2L);
        assertThat(chunks.getAllValues()).extracting(RagService.Chunk::chunkId).doesNotHaveDuplicates();
        verify(rag).deleteVersion(1);
        verify(rag).deleteVersion(2);
    }
}
