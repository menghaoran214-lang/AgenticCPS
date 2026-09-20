package com.qiji.cps.module.ai.service.capability;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiji.cps.framework.common.exception.ErrorCode;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.model.AiToolDO;
import com.qiji.cps.module.ai.dal.dataobject.knowledge.AiKnowledgeDO;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.dal.mysql.model.AiToolMapper;
import com.qiji.cps.module.ai.dal.mysql.knowledge.AiKnowledgeMapper;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolActionProvider;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Version content and bindings are frozen from review onward. Publication changes only the current pointer. */
@Service
@Validated
public class AiSkillServiceImpl implements AiSkillService {
    private static final ErrorCode INVALID = new ErrorCode(1_040_013_000, "Skill 配置无效：{}");
    private static final ErrorCode NOT_FOUND = new ErrorCode(1_040_013_001, "Skill 或版本不存在");
    private static final ErrorCode CONFLICT = new ErrorCode(1_040_013_002, "Skill 版本已被修改，请刷新后重试");
    private static final List<String> RISKS = List.of("READ_ONLY", "ATTRIBUTION_WRITE", "ASSET_WRITE", "ADMIN");
    @Resource private AiSkillMapper skillMapper;
    @Resource private AiSkillVersionMapper versionMapper;
    @Resource private AiSkillBindingMapper bindingMapper;
    @Resource private AiToolMapper toolMapper;
    @Resource private AiKnowledgeMapper knowledgeMapper;
    @Resource private AiMcpServerMapper serverMapper;
    @Resource private AiMcpToolMapper mcpToolMapper;
    @Resource private AiMcpPolicyMapper policyMapper;
    @Autowired(required = false)
    private List<AiCapabilityRiskProvider> riskProviders = Collections.emptyList();

    @Override
    public Long create(AiSkillSaveReqVO req) {
        validateSkill(req);
        require(skillMapper.selectOne(AiSkillDO::getCode, req.getCode()) == null, "编码已存在");
        AiSkillDO skill = BeanUtils.toBean(req, AiSkillDO.class).setId(null).setCurrentVersion(0);
        if (skill.getStatus() == null) skill.setStatus(0);
        if (skill.getRiskLevel() == null) skill.setRiskLevel("READ_ONLY");
        skillMapper.insert(skill);
        return skill.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiSkillSaveReqVO req) {
        AiSkillDO existing = lockSkill(req.getId());
        validateSkill(req);
        require(existing.getCode().equals(req.getCode()), "编码不可修改");
        // Risk is part of published capability semantics. Create a new Skill to change it after publication.
        if (existing.getCurrentVersion() != null && existing.getCurrentVersion() > 0) {
            require(Objects.equals(existing.getRiskLevel(), req.getRiskLevel()), "已发布 Skill 的风险级别不可修改");
        }
        skillMapper.updateById(BeanUtils.toBean(req, AiSkillDO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        lockSkill(id);
        require(versionMapper.selectListBySkillId(id).isEmpty(), "已有版本，请停用 Skill 而非删除");
        skillMapper.deleteById(id);
    }

    @Override public AiSkillDO get(Long id) { return requireSkill(id); }
    @Override public PageResult<AiSkillDO> page(AiSkillPageReqVO req) { return skillMapper.selectPage(req); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersion(AiSkillVersionSaveReqVO req) {
        lockSkill(req.getSkillId());
        validateContent(req);
        require(req.getPublishStatus() == null || "DRAFT".equals(req.getPublishStatus()), "新版本必须为草稿");
        int next = versionMapper.selectListBySkillId(req.getSkillId()).stream()
                .map(AiSkillVersionDO::getVersion).max(Integer::compareTo).orElse(0) + 1;
        require(req.getVersion() == null || req.getVersion() == next, "版本号由服务端递增分配");
        AiSkillVersionDO version = BeanUtils.toBean(req, AiSkillVersionDO.class)
                .setId(null).setVersion(next).setRevision(0L).setPublishStatus("DRAFT").setPublishedAt(null);
        if (version.getConfirmationPolicy() == null) version.setConfirmationPolicy("NONE");
        versionMapper.insert(version);
        return version.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVersion(AiSkillVersionSaveReqVO req) {
        AiSkillVersionDO version = lockVersion(req.getId());
        require("DRAFT".equals(version.getPublishStatus()), "仅草稿可编辑");
        checkRevision(version, req.getExpectedRevision());
        require(Objects.equals(version.getSkillId(), req.getSkillId()), "所属 Skill 不可修改");
        require(req.getVersion() == null || Objects.equals(version.getVersion(), req.getVersion()), "版本号不可修改");
        require(req.getPublishStatus() == null || "DRAFT".equals(req.getPublishStatus()), "请通过审核和发布操作变更状态");
        validateContent(req);
        AiSkillVersionDO update = BeanUtils.toBean(req, AiSkillVersionDO.class)
                .setVersion(version.getVersion()).setPublishStatus("DRAFT").setRevision(version.getRevision() + 1);
        versionMapper.updateById(update);
    }

    @Override public List<AiSkillVersionDO> versions(Long skillId) {
        requireSkill(skillId);
        return versionMapper.selectListBySkillId(skillId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewVersion(Long id) {
        AiSkillVersionDO version = lockVersion(id);
        require("DRAFT".equals(version.getPublishStatus()), "仅草稿可提交审核");
        validateBindings(requireSkill(version.getSkillId()), version, bindingMapper.selectListByVersionId(id), false);
        versionMapper.updateById(new AiSkillVersionDO().setId(id).setPublishStatus("REVIEWING")
                .setRevision(version.getRevision() + 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishVersion(Long id) {
        AiSkillVersionDO version = lockVersion(id);
        require("REVIEWING".equals(version.getPublishStatus()), "仅审核中的版本可发布");
        AiSkillDO skill = requireSkill(version.getSkillId());
        require(Integer.valueOf(0).equals(skill.getStatus()), "Skill 已停用");
        validateBindings(skill, version, bindingMapper.selectListByVersionId(id), true);
        versionMapper.updateById(new AiSkillVersionDO().setId(id).setPublishStatus("PUBLISHED")
                .setPublishedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))).setRevision(version.getRevision() + 1));
        skillMapper.updateById(new AiSkillDO().setId(skill.getId()).setCurrentVersion(version.getVersion()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveVersion(Long id) {
        AiSkillVersionDO version = lockVersion(id);
        require("PUBLISHED".equals(version.getPublishStatus()), "仅已发布版本可归档");
        AiSkillDO skill = requireSkill(version.getSkillId());
        require(!Objects.equals(skill.getCurrentVersion(), version.getVersion()), "请先切换当前版本再归档");
        versionMapper.updateById(new AiSkillVersionDO().setId(id).setPublishStatus("ARCHIVED")
                .setRevision(version.getRevision() + 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackVersion(Long skillId, Integer number) {
        AiSkillDO skill = lockSkill(skillId);
        AiSkillVersionDO target = versionMapper.selectBySkillIdAndVersion(skillId, number);
        if (target == null) throw exception(NOT_FOUND);
        require("PUBLISHED".equals(target.getPublishStatus()), "只能回滚至已发布版本");
        require(Integer.valueOf(0).equals(skill.getStatus()), "Skill 已停用");
        validateBindings(skill, target, bindingMapper.selectListByVersionId(target.getId()), true);
        skillMapper.updateById(new AiSkillDO().setId(skillId).setCurrentVersion(number));
    }

    @Override public AiSkillVersionDO getPublishedVersion(Long skillId) {
        AiSkillDO skill = requireSkill(skillId);
        require(Integer.valueOf(0).equals(skill.getStatus()), "Skill 已停用");
        AiSkillVersionDO version = versionMapper.selectBySkillIdAndVersion(skillId, skill.getCurrentVersion());
        require(version != null && "PUBLISHED".equals(version.getPublishStatus()), "Skill 没有可用的已发布版本");
        return version;
    }

    @Override public List<AiSkillBindingDO> bindings(Long id) {
        AiSkillVersionDO version = requireVersion(id);
        requireSkill(version.getSkillId());
        return bindingMapper.selectListByVersionId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBindings(Long id, Long expectedRevision, List<AiSkillBindingSaveReqVO> requests) {
        AiSkillVersionDO version = lockVersion(id);
        require("DRAFT".equals(version.getPublishStatus()), "仅草稿可修改绑定");
        checkRevision(version, expectedRevision);
        require(requests != null && requests.size() <= 100, "绑定数量不能超过 100");
        List<AiSkillBindingDO> bindings = new ArrayList<>();
        for (AiSkillBindingSaveReqVO req : requests) {
            require(req != null, "绑定不能为空");
            AiSkillBindingDO binding = BeanUtils.toBean(req, AiSkillBindingDO.class).setId(null).setSkillVersionId(id);
            if (binding.getEnabled() == null) binding.setEnabled(1);
            require(binding.getEnabled() == 0 || binding.getEnabled() == 1, "绑定启用值无效");
            bindings.add(binding);
        }
        validateBindings(requireSkill(version.getSkillId()), version, bindings, false);
        bindingMapper.delete(new LambdaQueryWrapper<AiSkillBindingDO>().eq(AiSkillBindingDO::getSkillVersionId, id));
        bindings.forEach(bindingMapper::insert);
        versionMapper.updateById(new AiSkillVersionDO().setId(id).setRevision(version.getRevision() + 1));
    }

    @Override public AiSkillTestRespVO test(AiSkillTestReqVO req) {
        AiSkillVersionDO version = requireVersion(req.getSkillVersionId());
        requireSkill(version.getSkillId());
        List<AiSkillBindingDO> all = bindings(version.getId());
        List<AiSkillBindingDO> readOnly = new ArrayList<>();
        for (AiSkillBindingDO binding : all) {
            if (!Integer.valueOf(1).equals(binding.getEnabled())) continue;
            try {
                if ("READ_ONLY".equals(bindingRisk(binding))) readOnly.add(binding);
            } catch (com.qiji.cps.framework.common.exception.ServiceException ignored) {
                // A disabled/unavailable binding is excluded; previews never execute tools.
            }
        }
        return new AiSkillTestRespVO().setSkillVersionId(version.getId()).setSystemPrompt(version.getSystemPrompt())
                .setInputSchema(version.getInputSchema()).setOutputTemplate(version.getOutputTemplate())
                .setInput(req.getInput()).setBindings(BeanUtils.toBean(readOnly, AiSkillBindingRespVO.class))
                .setExcludedBindingCount(all.size() - readOnly.size()).setExecutionMode("READ_ONLY_PREVIEW");
    }

    private void validateBindings(AiSkillDO skill, AiSkillVersionDO version, List<AiSkillBindingDO> bindings, boolean publishing) {
        Set<String> unique = new HashSet<>();
        for (AiSkillBindingDO binding : bindings) {
            String key = binding.getBindingType() + ":" + binding.getLocalToolName() + ":" + binding.getMcpServerId()
                    + ":" + binding.getMcpToolName() + ":" + binding.getKnowledgeBaseId();
            require(unique.add(key), "绑定重复");
            String risk = bindingRisk(binding);
            if (!Integer.valueOf(1).equals(binding.getEnabled())) continue;
            require(RISKS.indexOf(risk) <= RISKS.indexOf(skill.getRiskLevel()), "工具风险超出 Skill 上限");
            if (!"READ_ONLY".equals(risk)) require("REQUIRED".equals(version.getConfirmationPolicy()), "写操作必须配置用户确认");
            if (publishing && "MCP_TOOL".equals(binding.getBindingType())) validateMcpPolicy(skill, binding, risk);
        }
    }

    private String bindingRisk(AiSkillBindingDO binding) {
        require(binding.getBindingType() != null, "绑定类型不能为空");
        switch (binding.getBindingType()) {
            case "LOCAL_TOOL": {
                require(StrUtil.isNotBlank(binding.getLocalToolName()) && binding.getMcpServerId() == null
                        && binding.getMcpToolName() == null && binding.getKnowledgeBaseId() == null, "本地工具绑定无效");
                AiToolDO tool = toolMapper.selectOne(AiToolDO::getName, binding.getLocalToolName());
                require(tool != null && Integer.valueOf(0).equals(tool.getStatus()), "本地工具不存在或已停用");
                String risk = riskProviders.stream().map(provider -> provider.riskOf(binding.getLocalToolName()))
                        .filter(Objects::nonNull).max(Comparator.comparingInt(RISKS::indexOf)).orElse(null);
                require(risk != null && RISKS.contains(risk), "本地工具未登记风险");
                return risk;
            }
            case "MCP_TOOL": {
                require(binding.getMcpServerId() != null && StrUtil.isNotBlank(binding.getMcpToolName())
                        && binding.getLocalToolName() == null && binding.getKnowledgeBaseId() == null, "MCP 绑定无效");
                AiMcpServerDO server = serverMapper.selectById(binding.getMcpServerId());
                require(server != null && Integer.valueOf(0).equals(server.getStatus()), "MCP 服务不存在或已停用");
                AiMcpToolDO tool = findMcpTool(binding);
                require(tool != null && Integer.valueOf(0).equals(tool.getStatus()) && !"MISSING".equals(tool.getSyncStatus()), "MCP 工具不存在或不可用");
                require(RISKS.contains(tool.getRiskLevel()), "MCP 工具未登记风险");
                return tool.getRiskLevel();
            }
            case "KNOWLEDGE":
            case "KNOWLEDGE_BASE": {
                require(binding.getKnowledgeBaseId() != null && binding.getLocalToolName() == null
                        && binding.getMcpServerId() == null && binding.getMcpToolName() == null, "知识库绑定无效");
                AiKnowledgeDO knowledge = knowledgeMapper.selectById(binding.getKnowledgeBaseId());
                require(knowledge != null && Integer.valueOf(0).equals(knowledge.getStatus()), "知识库不存在或已停用");
                return "READ_ONLY";
            }
            default: throw exception(INVALID, "不支持的绑定类型");
        }
    }

    private AiMcpToolDO findMcpTool(AiSkillBindingDO binding) {
        return mcpToolMapper.selectOne(new LambdaQueryWrapper<AiMcpToolDO>()
                .eq(AiMcpToolDO::getServerId, binding.getMcpServerId()).eq(AiMcpToolDO::getName, binding.getMcpToolName()));
    }

    private void validateMcpPolicy(AiSkillDO skill, AiSkillBindingDO binding, String risk) {
        AiMcpToolDO tool = findMcpTool(binding);
        AiMcpServerDO server = serverMapper.selectById(binding.getMcpServerId());
        require("UP".equals(server.getHealthStatus()), "MCP 服务需先通过健康检查");
        List<AiMcpPolicyDO> policies = policyMapper.selectList(new LambdaQueryWrapper<AiMcpPolicyDO>()
                .eq(AiMcpPolicyDO::getSkillId, skill.getId()).eq(AiMcpPolicyDO::getStatus, 0));
        require(policies.stream().anyMatch(p -> RISKS.contains(p.getRiskLevel())
                && RISKS.indexOf(risk) <= RISKS.indexOf(p.getRiskLevel())
                && containsId(p.getMcpServerIds(), binding.getMcpServerId()) && containsId(p.getMcpToolIds(), tool.getId())
                && ("READ_ONLY".equals(risk) || Integer.valueOf(1).equals(p.getRequireConfirmation()))), "缺少完整的 Skill MCP 白名单策略");
    }

    private boolean containsId(String json, Long id) {
        try { return AiCapabilitySnapshotService.ids(json).contains(id); }
        catch (RuntimeException ignored) { return false; }
    }

    private AiSkillDO requireSkill(Long id) {
        if (id == null) throw exception(NOT_FOUND);
        AiSkillDO skill = skillMapper.selectById(id);
        if (skill == null) throw exception(NOT_FOUND);
        return skill;
    }
    private AiSkillDO lockSkill(Long id) {
        if (id == null) throw exception(NOT_FOUND);
        AiSkillDO skill = skillMapper.selectForUpdate(id);
        if (skill == null) throw exception(NOT_FOUND);
        return skill;
    }
    private AiSkillVersionDO requireVersion(Long id) {
        if (id == null) throw exception(NOT_FOUND);
        AiSkillVersionDO version = versionMapper.selectById(id);
        if (version == null) throw exception(NOT_FOUND);
        return version;
    }
    private AiSkillVersionDO lockVersion(Long id) {
        AiSkillVersionDO version = requireVersion(id);
        lockSkill(version.getSkillId());
        // A locking read sees committed changes even under MySQL REPEATABLE READ.
        AiSkillVersionDO locked = versionMapper.selectForUpdate(id);
        if (locked == null) throw exception(NOT_FOUND);
        return locked;
    }
    private void checkRevision(AiSkillVersionDO version, Long expected) {
        if (expected == null || !expected.equals(version.getRevision())) throw exception(CONFLICT);
    }
    private void validateSkill(AiSkillSaveReqVO req) {
        require(StrUtil.isNotBlank(req.getCode()) && req.getCode().matches("[a-zA-Z0-9_-]{1,64}"), "编码格式无效");
        require(StrUtil.isNotBlank(req.getName()) && req.getName().length() <= 128, "名称不能为空或超过 128 字符");
        require(req.getStatus() == null || req.getStatus() == 0 || req.getStatus() == 1, "状态无效");
        require(req.getRiskLevel() == null || RISKS.contains(req.getRiskLevel()), "风险级别无效");
    }
    private void validateContent(AiSkillVersionSaveReqVO req) {
        require(StrUtil.isNotBlank(req.getSystemPrompt()) && req.getSystemPrompt().length() <= 32000, "系统提示词不能为空或超过 32000 字符");
        require(req.getConfirmationPolicy() == null || List.of("NONE", "REQUIRED").contains(req.getConfirmationPolicy()), "确认策略无效");
        if (StrUtil.isNotBlank(req.getInputSchema())) {
            require(req.getInputSchema().length() <= 16000, "输入契约过长");
            try { require(JSONUtil.parseObj(req.getInputSchema()).containsKey("type"), "输入契约需声明 type"); }
            catch (cn.hutool.json.JSONException ex) { throw exception(INVALID, "输入契约必须是 JSON 对象"); }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw exception(INVALID, message); }
}
