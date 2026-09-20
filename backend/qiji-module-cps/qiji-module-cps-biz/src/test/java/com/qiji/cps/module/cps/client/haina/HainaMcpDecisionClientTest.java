package com.qiji.cps.module.cps.client.haina;

import com.qiji.cps.module.ai.service.capability.McpRegistryService;
import com.qiji.cps.module.cps.client.haina.dto.HainaDecisionRequest;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HainaMcpDecisionClientTest {

    @Test
    void callsDatabaseManagedHainaServer() {
        McpRegistryService registry = mock(McpRegistryService.class);
        McpSchema.CallToolResult result = McpSchema.CallToolResult.builder()
                .addTextContent("{}")
                .isError(false)
                .build();
        when(registry.callToolByCode(eq("haina"), eq("content_graph_search"), anyMap())).thenReturn(result);
        when(registry.callToolByCode(eq("haina"), eq("discount_search"), anyMap())).thenReturn(result);
        when(registry.callToolByCode(eq("haina"), eq("product_search_pro"), anyMap())).thenReturn(result);
        HainaMcpDecisionClient client = new HainaMcpDecisionClient(new HainaDecisionProperties(), registry);

        var evidence = client.collectEvidence(HainaDecisionRequest.builder().need("手机").build());

        assertTrue(evidence.getAvailable());
        verify(registry, times(3)).callToolByCode(eq("haina"), org.mockito.ArgumentMatchers.anyString(), anyMap());
    }

    @Test
    void unavailableDatabaseRegistrationFallsBackWithoutBreakingDecision() {
        McpRegistryService registry = mock(McpRegistryService.class);
        when(registry.callToolByCode(eq("haina"), eq("content_graph_search"), anyMap()))
                .thenThrow(new IllegalStateException("MCP service is disabled"));
        HainaMcpDecisionClient client = new HainaMcpDecisionClient(new HainaDecisionProperties(), registry);

        var evidence = client.collectEvidence(HainaDecisionRequest.builder().need("手机").build());

        assertFalse(evidence.getAvailable());
    }
}
