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
    void callsContentSearchToolWithLiveContract() {
        McpRegistryService registry = mock(McpRegistryService.class);
        McpSchema.CallToolResult result = McpSchema.CallToolResult.builder()
                .addTextContent("{\"error_code\":0,\"error_msg\":\"ok\",\"data\":{\"content_num\":1,\"content_list\":[{\"works_title\":\"无线鼠标好价\",\"works_content\":\"近期优惠信息\",\"works_link\":\"https://example.com/a\",\"publish_time\":\"2026-09-22\",\"website_cname\":\"什么值得买\"}]}}")
                .isError(false)
                .build();
        when(registry.callToolByCode(eq("haina"), eq("content_search"), anyMap())).thenReturn(result);
        HainaMcpDecisionClient client = new HainaMcpDecisionClient(new HainaDecisionProperties(), registry);

        var evidence = client.collectEvidence(HainaDecisionRequest.builder().need("手机").maxResults(3).build());

        assertTrue(evidence.getAvailable());
        assertEquals(1, evidence.getDiscounts().size());
        assertEquals("无线鼠标好价", evidence.getDiscounts().get(0).getTitle());
        assertEquals("什么值得买", evidence.getDiscounts().get(0).getMallName());
        assertEquals("近期优惠信息", evidence.getDiscounts().get(0).getContent());
        assertEquals("https://example.com/a", evidence.getDiscounts().get(0).getUrl());
        var args = forClass(Map.class);
        verify(registry).callToolByCode(eq("haina"), eq("content_search"), args.capture());
        assertEquals("手机", args.getValue().get("query"));
        assertEquals(3, args.getValue().get("return_size"));
        assertEquals(false, args.getValue().get("query_rewrite"));
        assertEquals(true, args.getValue().get("correlate_enhance"));
        assertFalse(args.getValue().containsKey("time_range"));
        assertFalse(args.getValue().containsKey("request_from"));
    }

    @Test
    void mapsLegacyContentSearchFieldsForPreviouslyReturnedPayloads() {
        McpRegistryService registry = mock(McpRegistryService.class);
        McpSchema.CallToolResult result = McpSchema.CallToolResult.builder()
                .addTextContent("{\"data\":{\"items\":[{\"title\":\"旧格式标题\",\"context\":\"旧格式内容\",\"article_link\":\"https://example.com/legacy\",\"source\":\"旧来源\"}]}}")
                .isError(false)
                .build();
        when(registry.callToolByCode(eq("haina"), eq("content_search"), anyMap())).thenReturn(result);
        HainaMcpDecisionClient client = new HainaMcpDecisionClient(new HainaDecisionProperties(), registry);

        var evidence = client.collectEvidence(HainaDecisionRequest.builder().need("手机").build());

        assertTrue(evidence.getAvailable());
        assertEquals("旧格式标题", evidence.getDiscounts().get(0).getTitle());
        assertEquals("旧来源", evidence.getDiscounts().get(0).getMallName());
        assertEquals("https://example.com/legacy", evidence.getDiscounts().get(0).getUrl());
    }

    @Test
    void unavailableDatabaseRegistrationFallsBackWithoutBreakingDecision() {
        McpRegistryService registry = mock(McpRegistryService.class);
        when(registry.callToolByCode(eq("haina"), eq("content_search"), anyMap()))
                .thenThrow(new IllegalStateException("MCP service is disabled"));
        HainaMcpDecisionClient client = new HainaMcpDecisionClient(new HainaDecisionProperties(), registry);

        var evidence = client.collectEvidence(HainaDecisionRequest.builder().need("手机").build());

        assertFalse(evidence.getAvailable());
    }
}
