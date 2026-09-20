package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpServerDO;
import java.util.List;
import java.util.Map;

/** Database-backed MCP registry. Connections are intentionally created by the runtime adapter on demand. */
public interface McpRegistryService {
    record Discovery(List<io.modelcontextprotocol.spec.McpSchema.Tool> tools, String version) { }
    Discovery discover(Long id);
    Discovery inspect(Long id);
    List<org.springframework.ai.tool.ToolCallback> getToolCallbacks(Long id);
    List<org.springframework.ai.tool.ToolCallback> getToolCallbacksByCode(String code);
    io.modelcontextprotocol.spec.McpSchema.CallToolResult callToolByCode(
            String code, String toolName, Map<String, Object> arguments);
    List<AiMcpServerDO> listEnabledServers();
    AiMcpServerDO getEnabledServer(Long id);
    void invalidate(Long id);
    void refreshHealth(Long id, boolean healthy, String error);
}
