package com.qiji.cps.module.cps.client.haina;

import com.qiji.cps.module.ai.service.capability.McpRegistryService;
import com.qiji.cps.module.cps.client.haina.dto.HainaDecisionRequest;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HainaMcpDecisionClientTest {

    @Test
    void callsNewContentSearchToolWithScreenshotContract() {
        McpRegistryService registry = mock(McpRegistryService.class);
        McpSchema.CallToolResult result = McpSchema.CallToolResult.builder()
                .addTextContent("{\"requestid\":\"req-1\",\"items\":[{\"title\":\"无线鼠标好价\",\"context\":\"近期优惠信息\",\"article_link\":\"https://example.com/a\",\"publish_time\":\"2026-09-22\",\"source\":\"什么值得买\",\"type\":\"web\"}]}")
                .isError(false)
                .build();
        when(registry.callToolByCode(eq("haina"), eq("content_search_pro"), anyMap())).thenReturn(result);
        HainaMcpDecisionClient client = new HainaMcpDecisionClient(new HainaDecisionProperties(), registry);

        var evidence = client.collectEvidence(HainaDecisionRequest.builder().need("手机").build());

        assertTrue(evidence.getAvailable());
        assertEquals(1, evidence.getDiscounts().size());
        assertEquals("无线鼠标好价", evidence.getDiscounts().get(0).getTitle());
        assertEquals("什么值得买", evidence.getDiscounts().get(0).getMallName());
        assertEquals("https://example.com/a", evidence.getDiscounts().get(0).getUrl());
        var args = forClass(Map.class);
        verify(registry).callToolByCode(eq("haina"), eq("content_search_pro"), args.capture());
        assertEquals("手机", args.getValue().get("query"));
        assertEquals("TwoYear", args.getValue().get("time_range"));
        assertEquals("content_search_pro_test", args.getValue().get("request_from"));
        assertEquals(10, args.getValue().get("recall_num"));
        assertEquals(true, args.getValue().get("ranker_advanced"));
        assertEquals(false, args.getValue().get("rewrite"));
        assertEquals("web", args.getValue().get("search_type"));
    }

    @Test
    void unavailableDatabaseRegistrationFallsBackWithoutBreakingDecision() {
        McpRegistryService registry = mock(McpRegistryService.class);
        when(registry.callToolByCode(eq("haina"), eq("content_search_pro"), anyMap()))
                .thenThrow(new IllegalStateException("MCP service is disabled"));
        HainaMcpDecisionClient client = new HainaMcpDecisionClient(new HainaDecisionProperties(), registry);

        var evidence = client.collectEvidence(HainaDecisionRequest.builder().need("手机").build());

        assertFalse(evidence.getAvailable());
    }
}
