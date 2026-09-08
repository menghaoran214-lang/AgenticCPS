package com.qiji.cps.module.ai.service.knowledge.splitter;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseSplitterTest {

    @Test
    void markdownQaSplitter_keepsEachShortQuestionAndAnswerTogether() {
        MarkdownQaSplitter splitter = new MarkdownQaSplitter(100);
        String content = """
                ## How is a CPS rebate credited?
                A rebate is credited only after the order reaches the settled state.

                ## Can an agent use a supplied member id?
                No. Member assets must use trusted login or tool context identity.
                """;

        List<Document> segments = splitter.apply(List.of(new Document(content)));

        assertEquals(2, segments.size());
        assertTrue(segments.get(0).getText().contains("How is a CPS rebate credited?"));
        assertTrue(segments.get(0).getText().contains("settled state"));
        assertTrue(segments.get(1).getText().contains("supplied member id"));
        assertTrue(segments.get(1).getText().contains("trusted login or tool context identity"));
    }

    @Test
    void markdownQaSplitter_repeatsQuestionWhenLongAnswerIsSplit() {
        MarkdownQaSplitter splitter = new MarkdownQaSplitter(28);
        String content = """
                ## What is the Token exchange safety rule?
                Only AVAILABLE rebate can be exchanged. Create an exchange order before freezing the rebate.
                If the remote request fails, unfreeze the rebate and keep an auditable failure reason.
                """;

        List<Document> segments = splitter.apply(List.of(new Document(content)));

        assertTrue(segments.size() > 1);
        assertTrue(segments.stream()
                .allMatch(segment -> segment.getText().startsWith("## What is the Token exchange safety rule?")));
    }

    @Test
    void semanticTextSplitter_splitsAtSentenceBoundaryAndIgnoresEmptyContent() {
        SemanticTextSplitter splitter = new SemanticTextSplitter(12, 0);
        String content = "Search goods first. Compare prices next. Generate a promotion link only after confirmation.";

        List<Document> segments = splitter.apply(List.of(new Document(content)));

        assertTrue(segments.size() > 1);
        assertTrue(segments.stream().allMatch(segment -> segment.getText().endsWith(".")));
        assertFalse(splitter.apply(List.of(new Document(""))).iterator().hasNext());
    }

}
