package com.qiji.cps.module.ai.service.capability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.enums.model.AiModelTypeEnum;
import com.qiji.cps.module.ai.enums.model.AiPlatformEnum;
import com.qiji.cps.module.ai.service.model.AiModelService;
import com.qiji.cps.module.ai.util.AiUtils;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit admin test execution. It cannot bind member identity or authorize writes. */
@Service
public class AiSkillSandboxService {
    @Resource private AiSkillService skills;
    @Resource private AiModelService models;
    @Resource private AiCapabilityRuntime runtime;
    @Resource private AiMcpServerMapper servers;
    @Resource private AiMcpToolMapper tools;
    @Resource private AiMcpPolicyMapper policies;

    public AiSkillTestRespVO test(AiSkillTestReqVO request) {
        AiSkillTestRespVO preview = skills.test(request);
        if (!Boolean.TRUE.equals(request.getExecuteModel())) return preview;
        if (request.getInput() == null || request.getInput().isBlank()) throw new IllegalArgumentException("请输入测试内容");
        var version = skills.versions(skillsVersionOwner(request.getSkillVersionId())).stream()
                .filter(item -> item.getId().equals(request.getSkillVersionId())).findFirst().orElseThrow();
        var skill = skills.get(version.getSkillId());
        var snapshot = new AiCapabilitySnapshot().setSchemaVersion(2).setRoleCode("skill-sandbox")
                .setRoleVersion(1).setRiskLevel("READ_ONLY").setAllowMutation(false);
        for (var binding : preview.getBindings()) {
            if ("KNOWLEDGE".equals(binding.getBindingType()) || "KNOWLEDGE_BASE".equals(binding.getBindingType())) continue;
            var frozen = new AiCapabilitySnapshot.Tool().setRiskLevel("READ_ONLY").setSkillCode(skill.getCode())
                    .setSkillVersion(version.getVersion()).setRequireConfirmation(false);
            if ("LOCAL_TOOL".equals(binding.getBindingType())) frozen.setName(binding.getLocalToolName());
            else if ("MCP_TOOL".equals(binding.getBindingType())) {
                AiMcpToolDO tool = tools.selectOne(new LambdaQueryWrapper<AiMcpToolDO>()
                        .eq(AiMcpToolDO::getServerId,binding.getMcpServerId()).eq(AiMcpToolDO::getName,binding.getMcpToolName()));
                if (tool == null || !"READ_ONLY".equals(tool.getRiskLevel()) || !Integer.valueOf(0).equals(tool.getStatus())) continue;
                boolean allowed = policies.selectList(new LambdaQueryWrapper<AiMcpPolicyDO>()
                        .eq(AiMcpPolicyDO::getSkillId,skill.getId()).eq(AiMcpPolicyDO::getStatus,0)).stream()
                        .anyMatch(policy -> AiCapabilitySnapshotService.ids(policy.getMcpToolIds()).contains(tool.getId())
                                && AiCapabilitySnapshotService.ids(policy.getMcpServerIds()).contains(tool.getServerId()));
                var server = servers.selectById(tool.getServerId());
                if (!allowed || server == null || !Integer.valueOf(0).equals(server.getStatus())) continue;
                frozen.setName(tool.getName()).setServerId(server.getId()).setInputSchema(tool.getInputSchema())
                        .setServerCode(server.getCode())
                        .setServerFingerprint(McpConnectionManager.fingerprint(server));
            } else continue;
            snapshot.getTools().add(frozen);
        }
        var conversation = new AiChatConversationDO().setOwnerUserType("ADMIN")
                .setUserId(SecurityFrameworkUtils.getLoginUserId()).setAllowMutation(false)
                .setEffectiveCapabilitySnapshot(JsonUtils.toJsonString(snapshot));
        AtomicInteger calls = new AtomicInteger();
        List<ToolCallback> callbacks = runtime.callbacks(conversation).stream().map(delegate -> (ToolCallback)new ToolCallback() {
            public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            public String call(String input) { return call(input,null); }
            public String call(String input, ToolContext context) {
                if (calls.incrementAndGet() > 8) throw new IllegalStateException("沙箱工具调用次数已达上限");
                return delegate.call(input,context);
            }
        }).toList();
        var model = request.getModelId() == null ? models.getRequiredDefaultModel(AiModelTypeEnum.CHAT.getType())
                : models.validateModel(request.getModelId());
        if (!Objects.equals(model.getType(),AiModelTypeEnum.CHAT.getType())) throw new IllegalArgumentException("请选择对话模型");
        var options = AiUtils.buildChatOptions(AiPlatformEnum.validatePlatform(model.getPlatform()),model.getModel(),
                model.getTemperature(),Math.min(4096,model.getMaxTokens() == null ? 4096 : model.getMaxTokens()),callbacks,Map.of());
        String prompt = Objects.toString(preview.getSystemPrompt(),"") + "\n输出要求："
                + Objects.toString(preview.getOutputTemplate(),"")
                + "\n当前为只读测试环境，不能执行写操作或访问会员资产，缺少证据时明确说明。";
        try {
            var response = models.getChatModel(model.getId()).call(new Prompt(List.of(new SystemMessage(prompt),
                    new UserMessage(request.getInput())),options));
            preview.setOutput(AiUtils.getChatResponseContent(response));
            return preview.setExecutionMode("READ_ONLY_EXECUTION");
        } catch (RuntimeException unavailable) {
            throw new IllegalStateException("测试模型或工具暂不可用，请检查配置后重试");
        }
    }

    @Resource private AiSkillVersionMapper versions;
    private Long skillsVersionOwner(Long versionId) {
        var version = versions.selectById(versionId);
        if (version == null) throw new IllegalArgumentException("Skill 版本不存在");
        return version.getSkillId();
    }
}
