package com.qiji.cps.module.ai.service.capability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpServerDO;
import com.qiji.cps.module.ai.dal.mysql.capability.AiMcpServerMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Validated
public class McpRegistryServiceImpl implements McpRegistryService {
    @Resource private AiMcpServerMapper serverMapper;
    @Resource private McpConnectionManager connections;
    @Resource private com.qiji.cps.module.ai.service.mcp.AiMcpIdentitySigner identitySigner;
    @Override public Discovery discover(Long id) {
        AiMcpServerDO server = getEnabledServer(id);
        if (server == null) throw new IllegalArgumentException("MCP 服务不存在或已停用");
        try {
            io.modelcontextprotocol.client.McpSyncClient client = connections.getClient(server);
            var discovery = readDiscovery(client);
            refreshHealth(id, true, null);
            return discovery;
        } catch (RuntimeException ex) {
            connections.invalidate(id);
            refreshHealth(id, false, "MCP_UNAVAILABLE");
            throw new IllegalStateException("MCP 服务不可用，请检查连接与认证配置");
        }
    }
    @Override public List<org.springframework.ai.tool.ToolCallback> getToolCallbacks(Long id) {
        Discovery discovery = discover(id);
        var server = getEnabledServer(id);
        if (server == null) return List.of();
        var client = connections.getClient(server);
        org.springframework.ai.mcp.ToolContextToMcpMetaConverter converter =
                "LOCAL".equals(server.getLocation()) && "cps".equals(server.getCode())
                ? new McpTrustedIdentityConverter(identitySigner, server.getCode())
                : org.springframework.ai.mcp.ToolContextToMcpMetaConverter.noOp();
        return discovery.tools().stream().map(tool -> (org.springframework.ai.tool.ToolCallback)
                org.springframework.ai.mcp.SyncMcpToolCallback.builder().mcpClient(client).tool(tool)
                        .prefixedToolName(tool.name()).toolContextToMcpMetaConverter(converter).build()).toList();
    }
    @Override public List<org.springframework.ai.tool.ToolCallback> getToolCallbacksByCode(String code) {
        AiMcpServerDO server = getEnabledServerByCode(code);
        return server == null ? List.of() : getToolCallbacks(server.getId());
    }
    @Override public io.modelcontextprotocol.spec.McpSchema.CallToolResult callToolByCode(
            String code, String toolName, Map<String, Object> arguments) {
        AiMcpServerDO server = getEnabledServerByCode(code);
        if (server == null) throw new IllegalStateException("MCP 服务不存在或已停用");
        try {
            var result = connections.getClient(server).callTool(
                    new io.modelcontextprotocol.spec.McpSchema.CallToolRequest(toolName, arguments));
            refreshHealth(server.getId(), true, null);
            return result;
        } catch (RuntimeException failure) {
            connections.invalidate(server.getId());
            refreshHealth(server.getId(), false, "MCP_UNAVAILABLE");
            throw new IllegalStateException("MCP 服务不可用，请检查连接与认证配置");
        }
    }
    @Override public Discovery inspect(Long id) {
        AiMcpServerDO server = serverMapper.selectById(id);
        if (server == null) throw new IllegalArgumentException("MCP 服务不存在");
        try (var client = connections.createInspectionClient(server)) {
            var discovery = readDiscovery(client);
            refreshHealth(id, true, null);
            return discovery;
        } catch (RuntimeException ex) {
            refreshHealth(id, false, "MCP_UNAVAILABLE");
            throw new IllegalStateException("MCP 服务不可用，请检查连接与认证配置");
        }
    }
    private Discovery readDiscovery(io.modelcontextprotocol.client.McpSyncClient client) {
        java.util.ArrayList<io.modelcontextprotocol.spec.McpSchema.Tool> tools = new java.util.ArrayList<>();
        java.util.Set<String> cursors = new java.util.HashSet<>();
        String cursor = null;
        do {
            var page = client.listTools(cursor);
            tools.addAll(page.tools());
            cursor = page.nextCursor();
            if (tools.size() > 2000 || (cursor != null && (!cursors.add(cursor) || cursors.size() >= 20)))
                throw new IllegalArgumentException("MCP 工具目录超出限制");
        } while (cursor != null && !cursor.isBlank());
        return new Discovery(List.copyOf(tools), client.getServerInfo().version());
    }
    @Override public List<AiMcpServerDO> listEnabledServers() {
        return serverMapper.selectList(new LambdaQueryWrapper<AiMcpServerDO>()
                .eq(AiMcpServerDO::getStatus, 0).orderByAsc(AiMcpServerDO::getId));
    }
    @Override public AiMcpServerDO getEnabledServer(Long id) {
        AiMcpServerDO server = serverMapper.selectById(id);
        return server != null && Integer.valueOf(0).equals(server.getStatus()) ? server : null;
    }
    private AiMcpServerDO getEnabledServerByCode(String code) {
        if (code == null || code.isBlank()) return null;
        return serverMapper.selectOne(new LambdaQueryWrapper<AiMcpServerDO>()
                .eq(AiMcpServerDO::getCode, code).eq(AiMcpServerDO::getStatus, 0));
    }
    @Override public void invalidate(Long id) {
        connections.invalidate(id);
        serverMapper.updateById(new AiMcpServerDO().setId(id).setHealthStatus("UNKNOWN")
                .setLastHealthCheckAt(LocalDateTime.now()));
    }
    @Override public void refreshHealth(Long id, boolean healthy, String error) {
        serverMapper.updateById(new AiMcpServerDO().setId(id)
                .setHealthStatus(healthy ? "UP" : "DOWN")
                .setLastError(healthy ? "" : "MCP_UNAVAILABLE").setLastHealthCheckAt(LocalDateTime.now()));
    }
}
