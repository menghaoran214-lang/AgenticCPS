package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpServerDO;
import com.qiji.cps.module.ai.dal.mysql.capability.AiMcpServerMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpRegistryServiceImplTest {
    private final AiMcpServerMapper mapper = mock(AiMcpServerMapper.class);
    private final McpConnectionManager connections = mock(McpConnectionManager.class);
    private final McpSyncClient client = mock(McpSyncClient.class);
    private final McpRegistryServiceImpl registry = new McpRegistryServiceImpl();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(registry, "serverMapper", mapper);
        ReflectionTestUtils.setField(registry, "connections", connections);
        AiMcpServerDO server = new AiMcpServerDO().setId(3L).setStatus(0);
        when(mapper.selectById(3L)).thenReturn(server);
        when(connections.getClient(server)).thenReturn(client);
    }
    @Test void discoversAllPagesBeforeReportingHealthy() {
        var first = McpSchema.Tool.builder("first", Map.of("type", "object")).build();
        var second = McpSchema.Tool.builder("second", Map.of("type", "object")).build();
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(List.of(first), "next"));
        when(client.listTools("next")).thenReturn(new McpSchema.ListToolsResult(List.of(second), null));
        when(client.getServerInfo()).thenReturn(new McpSchema.Implementation("sample", "2"));
        var result = registry.discover(3L);
        assertEquals(List.of(first, second), result.tools());
        assertEquals("2", result.version());
        verify(connections, never()).invalidate(anyLong());
    }
    @Test void connectionFailureInvalidatesOnlyFailedServerAndNeverReturnsCredentials() {
        when(client.listTools((String) null)).thenThrow(new IllegalStateException("Authorization: secret-value"));
        var error = assertThrows(IllegalStateException.class, () -> registry.discover(3L));
        assertFalse(error.getMessage().contains("secret-value"));
        assertNull(error.getCause());
        verify(connections).invalidate(3L);
        var update = ArgumentCaptor.forClass(AiMcpServerDO.class);
        verify(mapper).updateById(update.capture());
        assertEquals("DOWN", update.getValue().getHealthStatus());
        assertEquals("MCP_UNAVAILABLE", update.getValue().getLastError());
    }
    @Test void repeatedPaginationCursorCannotLoopIndefinitely() {
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(List.of(), "loop"));
        when(client.listTools("loop")).thenReturn(new McpSchema.ListToolsResult(List.of(), "loop"));
        assertThrows(IllegalStateException.class, () -> registry.discover(3L));
        verify(client, times(1)).listTools("loop");
    }

    @Test void resolvesCallbacksByDatabaseServerCode() {
        AiMcpServerDO server = new AiMcpServerDO().setId(3L).setCode("external").setStatus(0)
                .setLocation("REMOTE");
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(server);
        var tool = McpSchema.Tool.builder("external_search", Map.of("type", "object")).build();
        when(client.listTools((String) null)).thenReturn(new McpSchema.ListToolsResult(List.of(tool), null));
        when(client.getServerInfo()).thenReturn(new McpSchema.Implementation("sample", "1"));

        var callbacks = registry.getToolCallbacksByCode("external");

        assertEquals(List.of("external_search"), callbacks.stream()
                .map(callback -> callback.getToolDefinition().name()).toList());
        verify(mapper).selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void unknownDatabaseServerCodeDoesNotFallBackToConfiguredClients() {
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);

        assertTrue(registry.getToolCallbacksByCode("missing").isEmpty());
        verifyNoInteractions(connections);
    }
}
