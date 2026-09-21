package com.qiji.cps.module.ai.service.chat;

import cn.hutool.core.collection.CollUtil;
import com.qiji.cps.framework.common.util.collection.CollectionUtils;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiToolDO;
import com.qiji.cps.module.ai.service.model.AiChatRoleService;
import com.qiji.cps.module.ai.service.model.AiToolService;
import com.qiji.cps.module.ai.service.capability.AiSkillService;
import com.qiji.cps.module.ai.service.capability.AiCapabilitySnapshot;
import com.qiji.cps.module.ai.service.capability.AiCapabilitySnapshotService;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiSkillBindingDO;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiSkillDO;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiSkillVersionDO;
import com.qiji.cps.module.ai.service.capability.McpRegistryService;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolAction;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolExecutionEvent;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolExecutionListener;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assembles role-configured local and external tool callbacks for a conversation.
 */
@Service
@Slf4j
public class AiChatToolCallbackService {

    @Resource
    private AiChatRoleService chatRoleService;
    @Resource
    private AiToolService toolService;
    @Resource
    private com.qiji.cps.module.ai.service.capability.AiCapabilityRuntime capabilityRuntime;
    @Resource
    private ToolCallbackResolver toolCallbackResolver;
    @Resource
    private McpRegistryService mcpRegistryService;

    public List<ToolCallback> getToolCallbacks(AiChatConversationDO conversation) {
        return getToolCallbacks(conversation, Collections.emptyMap(), AiChatToolExecutionListener.NOOP);
    }

    public List<ToolCallback> getToolCallbacks(AiChatConversationDO conversation,
                                               Map<String, AiChatToolAction> actionsByToolName,
                                               AiChatToolExecutionListener listener) {
        if (conversation.getEffectiveCapabilitySnapshot() != null && capabilityRuntime != null) {
            Map<String, ToolSource> sources = snapshotToolSources(conversation);
            return capabilityRuntime.callbacks(conversation).stream().map(callback -> {
                AiChatToolAction action = actionsByToolName.get(callback.getToolDefinition().name());
                ToolSource source = sources.get(callback.getToolDefinition().name());
                return action == null ? callback : new ObservableToolCallback(callback, action, source, listener);
            }).toList();
        }
        if (conversation.getRoleId() == null) {
            return Collections.emptyList();
        }
        AiChatRoleDO chatRole = chatRoleService.getChatRole(conversation.getRoleId());
        chatRole = snapshotRole(chatRole, conversation);
        if (chatRole == null) {
            return Collections.emptyList();
        }
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        Map<String, ToolSource> sources = new java.util.HashMap<>();
        if ("SELF_MCP_TEST".equals(conversation.getChatMode())) {
            addSelfMcpCallbacks(conversation, toolCallbacks, sources);
        } else {
            addLocalCallbacks(chatRole, toolCallbacks, sources);
            addExternalMcpCallbacks(chatRole, toolCallbacks, sources);
        }
        if (CollUtil.isEmpty(actionsByToolName)) {
            return toolCallbacks;
        }
        return toolCallbacks.stream().map(callback -> {
            AiChatToolAction action = actionsByToolName.get(callback.getToolDefinition().name());
            ToolSource source = sources.get(callback.getToolDefinition().name());
            return action == null ? callback : new ObservableToolCallback(callback, action, source, listener);
        }).toList();
    }

    private Map<String, ToolSource> snapshotToolSources(AiChatConversationDO conversation) {
        if (conversation.getEffectiveCapabilitySnapshot() == null
                || conversation.getEffectiveCapabilitySnapshot().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            AiCapabilitySnapshot snapshot = AiCapabilitySnapshotService.read(conversation);
            if (snapshot == null || CollUtil.isEmpty(snapshot.getTools())) {
                return Collections.emptyMap();
            }
            Map<String, ToolSource> result = new java.util.HashMap<>();
            snapshot.getTools().forEach(tool -> result.put(tool.getName(), new ToolSource(
                    tool.getServerId() == null ? "LOCAL" : "MCP", tool.getServerCode(),
                    tool.getSkillCode(), tool.getSkillVersion())));
            return result;
        } catch (RuntimeException ex) {
            log.warn("[snapshotToolSources] 会话 [{}] 能力快照不可用", conversation.getId());
            return Collections.emptyMap();
        }
    }

    /**
     * 会话创建时会保存能力快照。优先使用快照中的工具/MCP 白名单，避免角色编辑后历史会话能力漂移。
     */
    @SuppressWarnings("unchecked")
    private AiChatRoleDO snapshotRole(AiChatRoleDO current, AiChatConversationDO conversation) {
        if (current == null && conversation.getEffectiveCapabilitySnapshot() == null) {
            return null;
        }
        AiChatRoleDO role = new AiChatRoleDO();
        if (conversation.getEffectiveCapabilitySnapshot() == null
                || conversation.getEffectiveCapabilitySnapshot().isBlank()) {
            return current;
        }
        try {
            Map<String, Object> snapshot = JsonUtils.parseObject(conversation.getEffectiveCapabilitySnapshot(), Map.class);
            Object toolIds = snapshot.get("toolIds");
            if (toolIds != null) {
                role.setToolIds(JsonUtils.parseArray(JsonUtils.toJsonString(toolIds), Long.class));
            }
            Object mcpNames = snapshot.get("mcpClientNames");
            if (mcpNames != null) {
                role.setMcpClientNames(JsonUtils.parseArray(JsonUtils.toJsonString(mcpNames), String.class));
            }
            if (conversation.getSkillSnapshot() != null && !conversation.getSkillSnapshot().isBlank()
                    && !"null".equals(conversation.getSkillSnapshot())) {
                role.setSkillIds(JsonUtils.parseArray(conversation.getSkillSnapshot(), Long.class));
            } else if (snapshot.get("skillIds") != null) {
                role.setSkillIds(JsonUtils.parseArray(JsonUtils.toJsonString(snapshot.get("skillIds")), Long.class));
            }
            Object allowMutation = snapshot.get("allowMutation");
            if (allowMutation instanceof Boolean) {
                role.setAllowMutation((Boolean) allowMutation);
            }
            Object riskLevel = snapshot.get("riskLevel");
            if (riskLevel != null) {
                role.setRiskLevel(String.valueOf(riskLevel));
            }
            return role;
        } catch (RuntimeException ex) {
            log.warn("[snapshotRole] 会话 [{}] 能力快照不可用", conversation.getId());
            return new AiChatRoleDO();
        }
    }

    private void addLocalCallbacks(AiChatRoleDO chatRole, List<ToolCallback> toolCallbacks,
                                   Map<String, ToolSource> sources) {
        if (CollUtil.isEmpty(chatRole.getToolIds())) {
            return;
        }
        Set<String> toolNames = CollectionUtils.convertSet(toolService.getToolList(chatRole.getToolIds()), AiToolDO::getName);
        toolNames.forEach(toolName -> {
            ToolCallback toolCallback = toolCallbackResolver.resolve(toolName);
            if (toolCallback != null) {
                if (addCallbackIfAbsent(toolCallback, toolCallbacks)) {
                    sources.putIfAbsent(toolCallback.getToolDefinition().name(), new ToolSource("LOCAL", null, null, null));
                }
            }
        });
    }

    private boolean addCallbackIfAbsent(ToolCallback callback, List<ToolCallback> callbacks) {
        if (callback == null || callbacks.stream().anyMatch(item ->
                item.getToolDefinition().name().equals(callback.getToolDefinition().name()))) {
            return false;
        }
        callbacks.add(callback);
        return true;
    }

    private void addExternalMcpCallbacks(AiChatRoleDO chatRole, List<ToolCallback> toolCallbacks,
                                         Map<String, ToolSource> sources) {
        if (CollUtil.isEmpty(chatRole.getMcpClientNames())) {
            return;
        }
        chatRole.getMcpClientNames().forEach(code -> addDatabaseMcpCallbacks(code, toolCallbacks, sources, "MCP"));
    }

    private void addSelfMcpCallbacks(AiChatConversationDO conversation, List<ToolCallback> toolCallbacks,
                                     Map<String, ToolSource> sources) {
        if (conversation.getMcpClientName() == null || conversation.getMcpClientName().isBlank()) {
            log.warn("[addSelfMcpCallbacks] 自测会话 [{}] 未配置可用的 MCP Client", conversation.getId());
            return;
        }
        addDatabaseMcpCallbacks(conversation.getMcpClientName(), toolCallbacks, sources, "SELF_MCP");
    }

    private void addDatabaseMcpCallbacks(String code, List<ToolCallback> toolCallbacks,
                                         Map<String, ToolSource> sources, String source) {
        try {
            mcpRegistryService.getToolCallbacksByCode(code)
                    .forEach(callback -> {
                        if (addCallbackIfAbsent(callback, toolCallbacks)) {
                            sources.putIfAbsent(callback.getToolDefinition().name(),
                                    new ToolSource(source, code, null, null));
                        }
                    });
        } catch (RuntimeException ex) {
            log.warn("[addDatabaseMcpCallbacks] MCP 服务 [{}] 暂不可用", code);
        }
    }

    private static final class ObservableToolCallback implements ToolCallback {

        private final ToolCallback delegate;
        private final AiChatToolAction action;
        private final ToolSource source;
        private final AiChatToolExecutionListener listener;

        private ObservableToolCallback(ToolCallback delegate, AiChatToolAction action,
                                       ToolSource source, AiChatToolExecutionListener listener) {
            this.delegate = delegate;
            this.action = action;
            this.source = source;
            this.listener = listener;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return delegate.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, new ToolContext(Map.of()));
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            String executionId = UUID.randomUUID().toString();
            listener.onEvent(event("TOOL_STARTED", executionId, "RUNNING", action.getRunningMessage()));
            try {
                String result = delegate.call(toolInput, toolContext);
                if (result != null && result.stripLeading().startsWith("{")) {
                    try {
                        Map<?,?> envelope = JsonUtils.parseObjectQuietly(result, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
                        if ("CONFIRM_REQUIRED".equals(envelope.get("status"))) {
                            listener.onEvent(event("TOOL_CONFIRM_REQUIRED",executionId,"WAITING_CONFIRMATION","等待用户确认"));
                            return result;
                        }
                        if ("DENIED".equals(envelope.get("status")) || "FAILED".equals(envelope.get("status"))) {
                            listener.onEvent(event("TOOL_FAILED",executionId,"FAILED","操作未执行或未完成，请查看业务提示"));
                            return result;
                        }
                    } catch (RuntimeException ignored) { /* Other tool result formats retain existing behavior. */ }
                }
                listener.onEvent(event("TOOL_SUCCEEDED", executionId, "SUCCEEDED", action.getSuccessMessage(), result));
                return result;
            } catch (RuntimeException ex) {
            listener.onEvent(event("TOOL_FAILED", executionId, "FAILED", "操作失败，请稍后重试", null));
                throw ex;
            }
        }

        private AiChatToolExecutionEvent event(String eventType, String executionId, String status, String message) {
            return event(eventType, executionId, status, message, null);
        }

        private AiChatToolExecutionEvent event(String eventType, String executionId, String status, String message,
                                               String resultPayload) {
            return new AiChatToolExecutionEvent().setEventType(eventType).setExecutionId(executionId)
                    .setToolName(action.getToolName()).setIntent(action.getIntent()).setLabel(action.getLabel())
                    .setStatus(status).setMessage(message).setResultPayload(resultPayload)
                    .setSource(source == null ? null : source.source())
                    .setMcpClientName(source == null ? null : source.mcpClientName())
                    .setSkillCode(source == null ? null : source.skillCode())
                    .setSkillVersion(source == null ? null : source.skillVersion());
        }
    }

    private record ToolSource(String source, String mcpClientName, String skillCode, Integer skillVersion) { }

}
