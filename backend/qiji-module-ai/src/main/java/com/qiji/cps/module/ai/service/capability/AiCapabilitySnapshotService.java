package com.qiji.cps.module.ai.service.capability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.service.model.AiToolService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AiCapabilitySnapshotService {
    @Value("${ai.capability-center.v2:false}")
    private boolean enabled;
    @Resource private AiSkillService skillService;
    @Resource private AiToolService toolService;
    @Resource private AiMcpPolicyMapper policyMapper;
    @Resource private AiMcpToolMapper mcpToolMapper;
    @Resource private AiMcpServerMapper serverMapper;
    @Autowired(required = false) private List<AiCapabilityRiskProvider> riskProviders = List.of();

    public boolean isEnabled() { return enabled; }

    @Transactional(readOnly = true)
    public void capture(AiChatConversationDO conversation, AiChatRoleDO role) {
        if (role == null) return;
        // 会员端保留旧会话语义，直到能力中心开关显式开启。
        if (!enabled && "MEMBER".equals(conversation.getOwnerUserType())) return;
        if (!enabled) {
            if (role.getSkillIds() != null && !role.getSkillIds().isEmpty())
                throw new IllegalArgumentException("AI 能力中心尚未启用，请使用兼容角色");
            return;
        }
        // Legacy roles may still select database-managed MCP clients by name. Keep
        // those roles on the legacy callback path until an explicit policy is set;
        // a client name alone must never become a v2 whole-server grant.
        if (hasLegacyMcpClientConfig(role)) {
            validateLegacyMcpConfig(role);
            if (!"PUBLISHED".equals(role.getPublishStatus())) {
                throw new IllegalArgumentException("角色未发布，不能创建新会话");
            }
            return;
        }
        capturePublished(conversation, role);
    }

    @Transactional(readOnly = true)
    public AiCapabilitySnapshot validateRole(AiChatRoleDO role) {
        validateLegacyMcpConfig(role);
        AiChatConversationDO preview = new AiChatConversationDO();
        capturePublished(preview, role);
        return read(preview);
    }

    private void capturePublished(AiChatConversationDO conversation, AiChatRoleDO role) {
        if (!"PUBLISHED".equals(role.getPublishStatus()))
            throw new IllegalArgumentException("角色未发布，不能创建新会话");
        AiCapabilitySnapshot snapshot = new AiCapabilitySnapshot();
        snapshot.setSchemaVersion(2);
        snapshot.setRoleCode(role.getPresetCode() == null ? "role-" + role.getId() : role.getPresetCode());
        snapshot.setRoleVersion(role.getVersion());
        snapshot.setRiskLevel(role.getRiskLevel() == null ? "READ_ONLY" : role.getRiskLevel());
        snapshot.setAllowMutation(Boolean.TRUE.equals(role.getAllowMutation()));
        snapshot.setKnowledgeIds(new ArrayList<>(role.getKnowledgeIds() == null ? List.of() : role.getKnowledgeIds()));
        AiMcpPolicyDO policy = role.getMcpPolicyId() == null ? null : policyMapper.selectById(role.getMcpPolicyId());
        if (role.getMcpPolicyId() != null && (policy == null || !Integer.valueOf(0).equals(policy.getStatus())))
            throw new IllegalArgumentException("角色 MCP 策略不可用");
        if (policy != null && policy.getRoleId() != null && !Objects.equals(policy.getRoleId(), role.getId()))
            throw new IllegalArgumentException("MCP 策略不属于当前角色");
        if (role.getToolIds() != null && !role.getToolIds().isEmpty()) {
            toolService.getToolList(role.getToolIds()).forEach(tool -> addLocal(snapshot, tool.getName(), null));
        }
        for (Long id : role.getSkillIds() == null ? List.<Long>of() : role.getSkillIds()) {
            AiSkillDO skill = skillService.get(id);
            if (rank(skill.getRiskLevel()) < 0 || rank(skill.getRiskLevel()) > rank(snapshot.getRiskLevel()))
                throw new IllegalArgumentException("Skill 风险超出角色上限");
            AiSkillVersionDO version = skillService.getPublishedVersion(id);
            var frozen = new AiCapabilitySnapshot.Skill().setId(id).setCode(skill.getCode())
                    .setVersionId(version.getId()).setVersion(version.getVersion())
                    .setSystemPrompt(version.getSystemPrompt()).setInputSchema(version.getInputSchema())
                    .setRiskLevel(skill.getRiskLevel())
                    .setOutputTemplate(version.getOutputTemplate()).setConfirmationPolicy(version.getConfirmationPolicy());
            snapshot.getSkills().add(frozen);
            for (AiSkillBindingDO binding : skillService.bindings(version.getId())) {
                if (!Integer.valueOf(1).equals(binding.getEnabled())) continue;
                switch (binding.getBindingType()) {
                    case "LOCAL_TOOL" -> addLocal(snapshot, binding.getLocalToolName(), frozen);
                    case "MCP_TOOL" -> addMcp(snapshot, policy, binding.getMcpServerId(), binding.getMcpToolName(), frozen);
                    case "KNOWLEDGE", "KNOWLEDGE_BASE" -> snapshot.getKnowledgeIds().add(binding.getKnowledgeBaseId());
                    default -> throw new IllegalArgumentException("不支持的能力绑定类型");
                }
            }
        }
        // Direct MCP grants require explicit tool IDs; a server name never grants its entire catalog.
        if (policy != null && policy.getSkillId() == null) for (Long toolId : ids(policy.getMcpToolIds())) {
            AiMcpToolDO tool = mcpToolMapper.selectById(toolId);
            if (tool != null) addMcp(snapshot, policy, tool.getServerId(), tool.getName(), null);
        }
        snapshot.setKnowledgeIds(snapshot.getKnowledgeIds().stream().distinct().toList());
        conversation.setRolePresetCode(role.getPresetCode()).setRoleVersion(role.getVersion())
                .setAllowMutation(Boolean.TRUE.equals(role.getAllowMutation()))
                .setEffectiveCapabilitySnapshot(JsonUtils.toJsonString(snapshot))
                .setSkillSnapshot(JsonUtils.toJsonString(snapshot.getSkills()))
                .setMcpPolicySnapshot(JsonUtils.toJsonString(policy));
        StringBuilder prompt = new StringBuilder(Objects.toString(role.getSystemMessage(), ""));
        for (var skill : snapshot.getSkills()) {
            if (skill.getSystemPrompt() != null) prompt.append("\n\n").append(skill.getSystemPrompt());
            if (skill.getOutputTemplate() != null) prompt.append("\n输出要求：").append(skill.getOutputTemplate());
        }
        conversation.setSystemMessage(prompt.toString());
    }

    private void addLocal(AiCapabilitySnapshot snapshot, String name, AiCapabilitySnapshot.Skill skill) {
        String risk = riskProviders.stream().map(provider -> provider.riskOf(name)).filter(Objects::nonNull)
                .max(Comparator.comparingInt(AiCapabilitySnapshotService::rank)).orElse(null);
        if (risk == null) throw new IllegalArgumentException("工具尚未登记风险，不能发布到会话");
        add(snapshot, name, null, risk, skill);
    }

    private void addMcp(AiCapabilitySnapshot snapshot, AiMcpPolicyDO policy, Long serverId,
                        String name, AiCapabilitySnapshot.Skill skill) {
        if (policy == null || !ids(policy.getMcpServerIds()).contains(serverId)) return;
        if (skill != null && policy.getSkillId() != null && !Objects.equals(policy.getSkillId(), skill.getId())) return;
        AiMcpServerDO server = serverMapper.selectById(serverId);
        if (server == null || !Integer.valueOf(0).equals(server.getStatus())) return;
        AiMcpToolDO tool = mcpToolMapper.selectOne(new LambdaQueryWrapper<AiMcpToolDO>()
                .eq(AiMcpToolDO::getServerId, serverId).eq(AiMcpToolDO::getName, name));
        if (tool == null || !Integer.valueOf(0).equals(tool.getStatus())
                || !ids(policy.getMcpToolIds()).contains(tool.getId())
                || rank(tool.getRiskLevel()) > rank(policy.getRiskLevel())) return;
        boolean confirmation = Integer.valueOf(1).equals(policy.getRequireConfirmation());
        if (rank(tool.getRiskLevel()) > 0 && !confirmation) return;
        if (skill != null) {
            boolean skillAllowed = policyMapper.selectList(new LambdaQueryWrapper<AiMcpPolicyDO>()
                    .eq(AiMcpPolicyDO::getSkillId, skill.getId()).eq(AiMcpPolicyDO::getStatus, 0)).stream()
                    .anyMatch(item -> ids(item.getMcpServerIds()).contains(serverId)
                            && ids(item.getMcpToolIds()).contains(tool.getId())
                            && rank(item.getRiskLevel()) >= rank(tool.getRiskLevel())
                            && (rank(tool.getRiskLevel()) == 0 || Integer.valueOf(1).equals(item.getRequireConfirmation())));
            if (!skillAllowed) return;
        }
        add(snapshot, name, serverId, tool.getRiskLevel(), skill);
        snapshot.getTools().stream().filter(item -> name.equals(item.getName())).findFirst().ifPresent(item ->
                item.setTitle(tool.getTitle()).setInputSchema(tool.getInputSchema())
                        .setServerCode(server.getCode())
                        .setServerFingerprint(McpConnectionManager.fingerprint(server))
                        .setRequireConfirmation(confirmation || Boolean.TRUE.equals(item.getRequireConfirmation())));
    }

    private void add(AiCapabilitySnapshot snapshot, String name, Long serverId, String risk, AiCapabilitySnapshot.Skill skill) {
        if (rank(risk) < 0 || rank(risk) > rank(snapshot.getRiskLevel())) return;
        if (skill != null && (rank(risk) > rank(skill.getRiskLevel())
                || (rank(risk) > 1 && !"REQUIRED".equals(skill.getConfirmationPolicy())))) return;
        if (!"READ_ONLY".equals(risk) && !Boolean.TRUE.equals(snapshot.getAllowMutation())) return;
        var existing = snapshot.getTools().stream().filter(tool -> name.equals(tool.getName())).findFirst();
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getServerId(), serverId)) throw new IllegalArgumentException("工具名称冲突");
            if (skill != null && "REQUIRED".equals(skill.getConfirmationPolicy()) && rank(risk) > 1) existing.get().setRequireConfirmation(true);
            return;
        }
        snapshot.getTools().add(new AiCapabilitySnapshot.Tool().setName(name).setServerId(serverId).setRiskLevel(risk)
                .setRequireConfirmation(rank(risk) > 1 || (skill != null && "REQUIRED".equals(skill.getConfirmationPolicy()) && rank(risk) > 1))
                .setSkillCode(skill == null ? null : skill.getCode()).setSkillVersion(skill == null ? null : skill.getVersion()));
    }

    public static AiCapabilitySnapshot read(AiChatConversationDO conversation) {
        if (conversation.getEffectiveCapabilitySnapshot() == null) return null;
        try {
            var snapshot = JsonUtils.parseObjectQuietly(conversation.getEffectiveCapabilitySnapshot(),
                    new com.fasterxml.jackson.core.type.TypeReference<AiCapabilitySnapshot>() {});
            if (snapshot == null || !Integer.valueOf(2).equals(snapshot.getSchemaVersion()) || snapshot.getTools() == null
                    || snapshot.getKnowledgeIds() == null || snapshot.getSkills() == null)
                throw new IllegalArgumentException("invalid snapshot");
            return snapshot;
        } catch (RuntimeException error) { throw new IllegalStateException("会话能力快照不可用，请新建会话"); }
    }
    public static int rank(String risk) { return List.of("READ_ONLY", "ATTRIBUTION_WRITE", "ASSET_WRITE", "ADMIN").indexOf(risk == null ? "" : risk); }
    public static Set<Long> ids(String value) {
        if (value == null || value.isBlank()) return Set.of();
        String normalized = value.replace("[", "").replace("]", "");
        Set<Long> result = new LinkedHashSet<>();
        for (String part : normalized.split(",")) if (!part.isBlank()) result.add(Long.valueOf(part.trim()));
        return result;
    }

    private static boolean hasLegacyMcpClientConfig(AiChatRoleDO role) {
        return role.getMcpPolicyId() == null && role.getMcpClientNames() != null
                && role.getMcpClientNames().stream().anyMatch(name -> name != null && !name.isBlank());
    }

    private static void validateLegacyMcpConfig(AiChatRoleDO role) {
        if (hasLegacyMcpClientConfig(role) && role.getSkillIds() != null && !role.getSkillIds().isEmpty()) {
            throw new IllegalArgumentException("绑定 Skill 的角色必须配置 MCP 策略");
        }
    }
}
