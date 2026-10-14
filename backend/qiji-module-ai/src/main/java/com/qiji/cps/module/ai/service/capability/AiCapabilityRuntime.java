package com.qiji.cps.module.ai.service.capability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.framework.tenant.core.util.TenantUtils;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiToolConfirmationDO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.mysql.capability.AiToolConfirmationMapper;
import com.qiji.cps.module.ai.dal.mysql.capability.AiMcpToolMapper;
import com.qiji.cps.module.ai.dal.dataobject.capability.AiMcpToolDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiToolDO;
import com.qiji.cps.module.ai.dal.mysql.model.AiToolMapper;
import com.qiji.cps.module.ai.dal.mysql.chat.AiChatMessageMapper;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatMessageDO;
import com.qiji.cps.module.ai.service.chat.AiChatConversationService;
import com.qiji.cps.module.ai.service.chat.AiChatIdentityContextService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class AiCapabilityRuntime {
    @Resource private McpRegistryService registry;
    @Resource private ToolCallbackResolver resolver;
    @Resource private AiToolConfirmationMapper confirmations;
    @Resource private AiChatConversationService conversations;
    @Resource private AiChatIdentityContextService identities;
    @Resource private AiCapabilityAuditService audit;
    @Resource private AiMcpToolMapper mcpTools;
    @Resource private AiToolMapper localTools;
    @Resource private AiChatMessageMapper messages;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private List<AiCapabilityRiskProvider> riskProviders = List.of();

    public List<ToolCallback> callbacks(AiChatConversationDO conversation) {
        AiCapabilitySnapshot snapshot;
        try { snapshot = AiCapabilitySnapshotService.read(conversation); }
        catch (IllegalStateException error) { return List.of(); }
        if (snapshot == null) return List.of();
        Long tenant = TenantContextHolder.getRequiredTenantId();
        Map<Long, List<ToolCallback>> servers = new HashMap<>();
        List<ToolCallback> result = new ArrayList<>();
        for (var tool : snapshot.getTools()) {
            ToolCallback callback = resolve(tool, servers);
            if (callback == null) continue;
            result.add(new ToolCallback() {
                public ToolDefinition getToolDefinition() { return callback.getToolDefinition(); }
                public ToolMetadata getToolMetadata() { return callback.getToolMetadata(); }
                public String call(String input) { return call(input, null); }
                public String call(String input, ToolContext supplied) {
                    return TenantUtils.execute(tenant, () -> invoke(conversation, snapshot, tool, callback, input, false));
                }
            });
        }
        return result;
    }

    private ToolCallback resolve(AiCapabilitySnapshot.Tool tool, Map<Long, List<ToolCallback>> servers) {
        if (tool.getServerId() == null) {
            try { return resolver.resolve(tool.getName()); }
            catch (RuntimeException unavailable) { return null; }
        }
        try {
            if (!Objects.equals(tool.getServerFingerprint(), McpConnectionManager.fingerprint(
                    registry.getEnabledServer(tool.getServerId())))) return null;
        } catch (RuntimeException unavailable) { return null; }
        return servers.computeIfAbsent(tool.getServerId(), id -> {
            try { return registry.getToolCallbacks(id); }
            catch (RuntimeException unavailable) { return List.of(); }
        }).stream().filter(callback -> tool.getName().equals(callback.getToolDefinition().name()))
                .filter(callback -> sameSchema(tool.getInputSchema(), callback.getToolDefinition().inputSchema()))
                .findFirst().orElse(null);
    }

    private String invoke(AiChatConversationDO conversation, AiCapabilitySnapshot snapshot,
                          AiCapabilitySnapshot.Tool tool, ToolCallback callback, String input, boolean confirmed) {
        if (input == null || input.length() > 16000) throw new IllegalArgumentException("操作参数过长");
        Map<?,?> arguments;
        try { arguments = JsonUtils.parseObjectQuietly(input, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}); }
        catch (RuntimeException invalid) { return rejection("DENIED", "操作参数格式无效"); }
        if (arguments == null || containsIdentity(arguments)) {
            audit.record(conversation, snapshot, tool, "DENY", "UNTRUSTED_IDENTITY", 0);
            return rejection("DENIED", "操作参数不能指定身份或认证信息，请使用已绑定的可信身份");
        }
        int risk = AiCapabilitySnapshotService.rank(tool.getRiskLevel());
        if (risk < 0 || risk > AiCapabilitySnapshotService.rank(snapshot.getRiskLevel())
                || (risk > 0 && !Boolean.TRUE.equals(snapshot.getAllowMutation()))) {
            audit.record(conversation, snapshot, tool, "DENY", "RISK_DENIED", 0);
            return rejection("DENIED", "当前角色未授权此操作");
        }
        if (!currentlyAllowed(tool)) {
            audit.record(conversation, snapshot, tool, "DENY", "CAPABILITY_CHANGED", 0);
            return rejection("DENIED", "能力已停用或配置已变化，请新建会话");
        }
        // Identity for all asset/order/link operations comes from the persisted binding, never model input.
        if (needsMember(tool) && conversation.getMemberId() == null) {
            audit.record(conversation, snapshot, tool, "DENY", "MEMBER_IDENTITY_REQUIRED", 0);
            return rejection("DENIED", "请先绑定可信会员身份后再执行此操作");
        }
        boolean requiresConfirmation = (risk > 1 || (Boolean.TRUE.equals(tool.getRequireConfirmation()) && risk > 1)) && !confirmed;
        if (requiresConfirmation) {
            AiToolConfirmationDO pending = new AiToolConfirmationDO().setConversationId(conversation.getId())
                    .setOwnerId(conversation.getUserId()).setToolName(tool.getName()).setArguments(input)
                    .setStatus("PENDING").setExpiresAt(now().plusMinutes(10));
            confirmations.insert(pending);
            audit.record(conversation, snapshot, tool, "CONFIRM_REQUIRED", null, 0);
            return JsonUtils.toJsonString(Map.of("status", "CONFIRM_REQUIRED", "confirmationId", pending.getId().toString(),
                    "message", "请在会话确认卡片中确认此操作，确认前未执行"));
        }
        long start = System.nanoTime();
        // Record authorization before invoking; an unavailable audit store must never silently permit writes.
        audit.record(conversation, snapshot, tool, "ALLOW", null, 0);
        try {
            Map<String,Object> context = identities.buildToolContext(conversation);
            context.put(com.qiji.cps.module.ai.util.AiUtils.TOOL_CONTEXT_ALLOW_MUTATION,
                    Boolean.TRUE.equals(snapshot.getAllowMutation()) && (risk <= 1 || confirmed));
            String result = callback.call(input, new ToolContext(context));
            audit.record(conversation, snapshot, tool, "SUCCEEDED", null, elapsed(start));
            return result;
        } catch (RuntimeException failure) {
            audit.record(conversation, snapshot, tool, "FAILED", "TOOL_UNAVAILABLE", elapsed(start));
            return rejection("FAILED", "操作失败，请核对业务状态后重试");
        }
    }

    public List<Map<String,Object>> pending(Long conversationId, Long ownerId) {
        var conversation = conversations.getOwnedConversation(conversationId, "ADMIN", ownerId);
        var snapshot = AiCapabilitySnapshotService.read(conversation);
        return confirmations.selectList(new LambdaQueryWrapper<AiToolConfirmationDO>()
                .eq(AiToolConfirmationDO::getConversationId, conversationId).eq(AiToolConfirmationDO::getOwnerId, ownerId)
                .eq(AiToolConfirmationDO::getStatus,"PENDING").gt(AiToolConfirmationDO::getExpiresAt,now())
                .orderByDesc(AiToolConfirmationDO::getId)).stream()
                .filter(item -> {
                    if (snapshot == null) return false;
                    return snapshot.getTools().stream()
                            .filter(t -> t.getName().equals(item.getToolName()))
                            .anyMatch(t -> AiCapabilitySnapshotService.rank(t.getRiskLevel()) > 1);
                })
                .map(item -> {
                    Map<String,Object> view = new LinkedHashMap<>();
                    view.put("id", item.getId().toString()); view.put("toolLabel", businessLabel(item.getToolName()));
                    view.put("summary", confirmationSummary(item.getArguments()));
                    view.put("status", item.getStatus()); view.put("expiresAt", item.getExpiresAt()); return view;
                }).toList();
    }

    public String confirm(Long id, Long ownerId) {
        AiToolConfirmationDO pending = confirmations.selectById(id);
        if (pending == null || !Objects.equals(pending.getOwnerId(),ownerId)) throw new IllegalArgumentException("确认记录不存在");
        var conversation = conversations.getOwnedConversation(pending.getConversationId(),"ADMIN",ownerId);
        var snapshot = AiCapabilitySnapshotService.read(conversation);
        var tool = snapshot.getTools().stream().filter(item -> item.getName().equals(pending.getToolName()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("能力已失效"));
        var callback = resolve(tool,new HashMap<>());
        if (callback == null) throw new IllegalStateException("服务暂不可用，请稍后重试");
        int changed = confirmations.update(null,new LambdaUpdateWrapper<AiToolConfirmationDO>()
                .eq(AiToolConfirmationDO::getId,id).eq(AiToolConfirmationDO::getStatus,"PENDING")
                .eq(AiToolConfirmationDO::getOwnerId,ownerId).gt(AiToolConfirmationDO::getExpiresAt,now())
                .set(AiToolConfirmationDO::getStatus,"EXECUTING"));
        if (changed != 1) throw new IllegalStateException("操作已处理或确认已过期");
        // Never retry a consumed confirmation; domain idempotency is still enforced by the existing tool.
        try {
            String result = invoke(conversation,snapshot,tool,callback,pending.getArguments(),true);
            messages.insert(new AiChatMessageDO().setConversationId(conversation.getId()).setUserId(ownerId)
                    .setRoleId(conversation.getRoleId()).setModelId(conversation.getModelId()).setModel(conversation.getModel())
                    .setType("assistant").setUseContext(false).setContent("已处理你确认的「" + businessLabel(tool.getName())
                            + "」。请核对本次操作结果及业务记录；该确认不能重复执行。"));
            return result;
        } finally {
            confirmations.update(null,new LambdaUpdateWrapper<AiToolConfirmationDO>().eq(AiToolConfirmationDO::getId,id)
                    .set(AiToolConfirmationDO::getStatus,"CONSUMED").set(AiToolConfirmationDO::getArguments,"{}"));
        }
    }
    private boolean needsMember(AiCapabilitySnapshot.Tool tool) {
        String name=tool.getName();
        return !"READ_ONLY".equals(tool.getRiskLevel()) || name.contains("order") || name.contains("rebate")
                || name.contains("exchange") || name.contains("conversion");
    }
    private boolean currentlyAllowed(AiCapabilitySnapshot.Tool tool) {
        try {
            if (tool.getServerId() == null) {
                AiToolDO current = localTools.selectOne(AiToolDO::getName,tool.getName());
                String risk = riskProviders.stream().map(provider -> provider.riskOf(tool.getName()))
                        .filter(Objects::nonNull).max(Comparator.comparingInt(AiCapabilitySnapshotService::rank)).orElse(null);
                return current != null && Integer.valueOf(0).equals(current.getStatus())
                        && risk != null && risk.equals(tool.getRiskLevel());
            }
            var server = registry.getEnabledServer(tool.getServerId());
            if (!Objects.equals(tool.getServerFingerprint(),McpConnectionManager.fingerprint(server))) return false;
            if (needsMember(tool) && !("LOCAL".equals(server.getLocation()) && "cps".equals(server.getCode()))) return false;
            AiMcpToolDO current = mcpTools.selectOne(new LambdaQueryWrapper<AiMcpToolDO>()
                    .eq(AiMcpToolDO::getServerId,tool.getServerId()).eq(AiMcpToolDO::getName,tool.getName()));
            return current != null && Integer.valueOf(0).equals(current.getStatus())
                    && !"MISSING".equals(current.getSyncStatus()) && Objects.equals(current.getRiskLevel(),tool.getRiskLevel());
        } catch (RuntimeException unavailable) { return false; }
    }
    private static boolean containsIdentity(Object value) {
        if (value instanceof Map<?,?> map) {
            for (var entry : map.entrySet()) {
                String key = entry.getKey().toString().replace("_", "").toLowerCase(Locale.ROOT);
                if (Set.of("memberid","userid","tenantid","actoruserid","actorusertype","authorization",
                        "apikey","accesstoken","password","signature","toolcontext","allowmutation").contains(key)
                        || containsIdentity(entry.getValue())) return true;
            }
        } else if (value instanceof Collection<?> collection) {
            return collection.stream().anyMatch(AiCapabilityRuntime::containsIdentity);
        }
        return false;
    }
    private static String rejection(String status, String message) { return JsonUtils.toJsonString(Map.of("status",status,"message",message)); }
    private static int elapsed(long start) { return (int)Math.min(Integer.MAX_VALUE,(System.nanoTime()-start)/1_000_000); }
    private static boolean sameSchema(String frozen, String current) {
        if (frozen == null || current == null) return false;
        try { return JsonUtils.parseObjectQuietly(frozen, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}).equals(JsonUtils.parseObjectQuietly(current, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {})); }
        catch (RuntimeException invalid) { return false; }
    }
    private static String businessLabel(String toolName) {
        if (toolName.contains("exchange")) return "返利兑换";
        if (toolName.contains("link") || toolName.contains("conversion")) return "生成推广链接";
        return "业务写入操作";
    }
    private static String confirmationSummary(String arguments) {
        try {
            Map<?,?> input = JsonUtils.parseObjectQuietly(arguments, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {});
            Map<String,String> labels = Map.of("platform", "平台", "goodsId", "商品", "keyword", "关键词",
                    "amountFen", "金额（分）", "rebateAmountFen", "返利金额（分）", "tokenCount", "Token 数量",
                    "amount", "金额（元）", "goods_id", "商品", "task_id", "任务");
            List<String> parts = new ArrayList<>();
            labels.forEach((key,label) -> {
                Object value = input.get(key);
                if (value instanceof String || value instanceof Number) {
                    String safe = value.toString().replaceAll("[\\p{Cntrl}]", " ");
                    parts.add(label + "：" + safe.substring(0,Math.min(safe.length(),120)));
                }
            });
            return parts.isEmpty() ? "确认后将执行一次业务操作，请核对本轮请求。" : String.join("；",parts);
        } catch (RuntimeException invalid) { return "请核对本轮请求中的业务操作。"; }
    }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
}
