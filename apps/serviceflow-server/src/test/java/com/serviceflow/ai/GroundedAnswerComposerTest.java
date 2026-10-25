package com.serviceflow.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceflow.rag.RagService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroundedAnswerComposerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generatedClaimsCannotReplaceServerOwnedFacts() throws Exception {
        String context = "{\"product\":\"Phone\",\"facts\":[{\"text\":\"最高50W，需要指定配件。\"}]}";
        String answer = GroundedAnswerComposer.compose(
                context, "{\"factIds\":[0,0],\"insufficient\":false,\"answer\":\"任何配件都支持100W\"}", mapper);
        assertThat(answer).isEqualTo("Phone：\n最高50W，需要指定配件。");
    }

    @Test
    void unknownIdsAndMalformedSelectionsCannotBePublished() {
        String context = "{\"product\":\"Phone\",\"facts\":[{\"text\":\"fact\"}]}";
        for (String selection : List.of(
                "{\"factIds\":[9],\"insufficient\":false}",
                "{\"factIds\":[0.5],\"insufficient\":false}",
                "{\"factIds\":[],\"insufficient\":false}",
                "{\"factIds\":[0]}",
                "unstructured invented answer")) {
            assertThatThrownBy(() -> GroundedAnswerComposer.compose(context, selection, mapper))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(GroundedAnswerComposer.compose(context, "{\"factIds\":[],\"insufficient\":true}", mapper))
                .contains("资料不足");
    }

    @Test
    void evidenceParagraphsKeepConditionsAndDoNotExposeRetrievalHeaders() throws Exception {
        var context = GroundedAnswerComposer.context(
                Map.of("名称", "Phone", "SKU", "PHONE-A-1", "报价", "暂无报价"),
                List.of(new RagService.Evidence(
                        "chunk",
                        "title",
                        "manual.md",
                        "# title\n\n来源：https://example.test\n\n## power：充电\n\n最高50W。需另购配件，实际功率变化。\n\n来源章节：充电",
                        1)));
        String json = mapper.writeValueAsString(context);
        assertThat(json).contains("最高50W。需另购配件，实际功率变化。", "暂无报价");
        assertThat(json).doesNotContain("https://", "来源章节", "## power");
    }
}
