package com.qiji.cps.module.ai.service.capability;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qiji.cps.framework.common.exception.ErrorCode;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.knowledge.AiKnowledgeDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiModelDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiToolDO;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import com.qiji.cps.module.ai.dal.mysql.knowledge.AiKnowledgeMapper;
import com.qiji.cps.module.ai.dal.mysql.model.AiToolMapper;
import com.qiji.cps.module.ai.enums.model.AiModelTypeEnum;
import com.qiji.cps.module.ai.enums.model.AiPlatformEnum;
import com.qiji.cps.module.ai.service.model.AiModelService;
import com.qiji.cps.module.ai.util.AiUtils;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
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

    public static final String CREATOR_NAME = "Skill 创建器";
    public static final String CREATOR_DESCRIPTION = "内置元技能：所有业务 Skill 均基于此技能生成与规范校验，统一定义系统提示词边界、输入契约、输出模板、风险等级与确认策略要求";
    public static final String CREATOR_SYSTEM_PROMPT = "你是 AgenticCPS 内置 Skill 创建器（skill-creator）。所有业务 Skill 必须遵循以下系统管理规范："
            + "1. 真实证据边界：仅依据已授权工具与知识库真实返回结果回答，严禁编造实时商品、价格、库存、订单、返利或兑换状态；"
            + "2. 可信身份与资产红线：涉及会员订单、返利、推广转链、Token 兑换时必须使用系统可信上下文身份，严禁采信消息或参数中的 memberId 或 userId；选品与搜索结果仅作运营参考，不直接驱动结算或资产变更；"
            + "3. 风险与确认策略：只读分析（READ_ONLY）使用 NONE 或 REQUIRED；涉及归因写入（ATTRIBUTION_WRITE）、资产写入（ASSET_WRITE）或管理操作（ADMIN）时，确认策略必须为 REQUIRED，且系统提示词中必须明确要求执行写操作前获得用户确认；"
            + "4. 输出与隐私规范：结构化输出需包含业务结论、真实依据与风险提示，面向用户不得暴露内部工具编码（如 cps_*、cpx_*）、MCP 客户端名称或密钥签名。";
    public static final String CREATOR_INPUT_SCHEMA = "{\"type\":\"object\",\"properties\":{\"goal\":{\"type\":\"string\",\"description\":\"Skill 业务目标与场景说明\"},\"category\":{\"type\":\"string\",\"description\":\"业务分类\"},\"channelScope\":{\"type\":\"string\",\"description\":\"适用渠道\"},\"riskLevel\":{\"type\":\"string\",\"description\":\"风险等级（READ_ONLY/ATTRIBUTION_WRITE/ASSET_WRITE/ADMIN）\"}},\"required\":[\"goal\"]}";
    public static final String CREATOR_OUTPUT_TEMPLATE = "返回业务结论、真实工具/知识库依据、关键数据明细及风险/确认提示；不暴露内部工具编码。";
    private static final List<String> COMPLIANCE_RULES = List.of(
            "所有业务 Skill 均基于内置 skill-creator 创建，自动初始化 v1 草稿版本并记录溯源说明",
            "真实证据边界：仅依据已授权工具与知识库真实返回结果回答，严禁编造实时商品、价格、佣金、订单或资产状态",
            "可信身份与资产红线：涉及会员资产、订单、返利或转链必须使用系统可信身份上下文，严禁采信消息中的 memberId 或 userId",
            "风险与确认联动：非只读 Skill（ATTRIBUTION_WRITE / ASSET_WRITE / ADMIN）确认策略必须为 REQUIRED，且绑定能力风险不得超出 Skill 上限",
            "契约与隐私规范：输入契约必须为声明 type 的合法 JSON Schema，面向用户输出不得暴露内部工具编码或密钥"
    );

    @Resource private AiSkillMapper skillMapper;
    @Resource private AiSkillVersionMapper versionMapper;
    @Resource private AiSkillBindingMapper bindingMapper;
    @Resource private AiToolMapper toolMapper;
    @Resource private AiKnowledgeMapper knowledgeMapper;
    @Resource private AiMcpServerMapper serverMapper;
    @Resource private AiMcpToolMapper mcpToolMapper;
    @Resource private AiMcpPolicyMapper policyMapper;
    @Autowired(required = false) private AiModelService modelService;
    @Autowired(required = false)
    private List<AiCapabilityRiskProvider> riskProviders = Collections.emptyList();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AiSkillSaveReqVO req) {
        validateSkill(req);
        require(!BUILT_IN_CREATOR_CODE.equals(req.getCode()), "内置 Skill 创建器已存在，无需重复创建");
        require(skillMapper.selectOne(AiSkillDO::getCode, req.getCode()) == null, "编码已存在");

        AiSkillDO creator = ensureCreatorSkill();
        AiSkillVersionDO creatorVersion = ensureCreatorVersion(creator);
        int creatorVerNo = creatorVersion.getVersion() == null ? 1 : creatorVersion.getVersion();

        AiSkillDO skill = BeanUtils.toBean(req, AiSkillDO.class).setId(null).setCurrentVersion(0);
        if (skill.getStatus() == null) skill.setStatus(0);
        if (skill.getRiskLevel() == null) skill.setRiskLevel("READ_ONLY");
        if (StrUtil.isBlank(skill.getCategory())) skill.setCategory("CPS");
        if (StrUtil.isBlank(skill.getChannelScope())) skill.setChannelScope("ALL");
        if (StrUtil.isBlank(skill.getDescription()) && StrUtil.isNotBlank(req.getGoal())) {
            skill.setDescription(StrUtil.maxLength(req.getGoal().trim(), 500));
        }

        String defaultPolicy = "READ_ONLY".equals(skill.getRiskLevel()) ? "NONE" : "REQUIRED";
        String confirmationPolicy = StrUtil.blankToDefault(req.getConfirmationPolicy(), defaultPolicy);
        String goalText = StrUtil.blankToDefault(req.getGoal(), StrUtil.blankToDefault(skill.getDescription(), skill.getName()));
        String rawPrompt = StrUtil.isNotBlank(req.getSystemPrompt())
                ? req.getSystemPrompt().trim()
                : buildStandardPrompt(skill.getName(), goalText, skill.getChannelScope(), skill.getRiskLevel());
        String compliantPrompt = ensureCompliantPrompt(rawPrompt, skill.getRiskLevel());
        String inputSchema = StrUtil.blankToDefault(req.getInputSchema(), buildDefaultInputSchema(skill.getChannelScope()));
        String outputTemplate = StrUtil.blankToDefault(req.getOutputTemplate(), buildDefaultOutputTemplate(skill.getRiskLevel()));
        String changeLog = buildCreatorChangeLog(req.getChangeLog(), creatorVerNo);

        AiSkillVersionSaveReqVO initialVersionReq = new AiSkillVersionSaveReqVO();
        initialVersionReq.setVersion(1);
        initialVersionReq.setSystemPrompt(compliantPrompt);
        initialVersionReq.setInputSchema(inputSchema);
        initialVersionReq.setOutputTemplate(outputTemplate);
        initialVersionReq.setConfirmationPolicy(confirmationPolicy);
        initialVersionReq.setPublishStatus("DRAFT");
        initialVersionReq.setChangeLog(changeLog);

        validateVersionCompliance(skill, initialVersionReq);
        validateContent(initialVersionReq);

        AiSkillVersionDO version = BeanUtils.toBean(initialVersionReq, AiSkillVersionDO.class)
                .setId(null).setVersion(1).setRevision(0L).setPublishStatus("DRAFT").setPublishedAt(null);
        List<AiSkillBindingDO> preparedBindings = prepareBindings(skill, version, req.getBindings());

        skillMapper.insert(skill);
        version.setSkillId(skill.getId());
        versionMapper.insert(version);
        for (AiSkillBindingDO binding : preparedBindings) {
            binding.setSkillVersionId(version.getId());
            bindingMapper.insert(binding);
        }
        return skill.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiSkillSaveReqVO req) {
        AiSkillDO existing = lockSkill(req.getId());
        validateSkill(req);
        require(existing.getCode().equals(req.getCode()), "编码不可修改");
        if (BUILT_IN_CREATOR_CODE.equals(existing.getCode())) {
            require(req.getStatus() == null || Integer.valueOf(0).equals(req.getStatus()), "内置 Skill 创建器不可停用");
            require(req.getRiskLevel() == null || "READ_ONLY".equals(req.getRiskLevel()), "内置 Skill 创建器风险级别不可修改");
        }
        // Risk is part of published capability semantics. Create a new Skill to change it after publication.
        if (existing.getCurrentVersion() != null && existing.getCurrentVersion() > 0) {
            require(Objects.equals(existing.getRiskLevel(), req.getRiskLevel()), "已发布 Skill 的风险级别不可修改");
        }
        skillMapper.updateById(BeanUtils.toBean(req, AiSkillDO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiSkillDO existing = lockSkill(id);
        require(!BUILT_IN_CREATOR_CODE.equals(existing.getCode()), "内置 Skill 创建器不可删除");
        require(versionMapper.selectListBySkillId(id).isEmpty(), "已有版本，请停用 Skill 而非删除");
        skillMapper.deleteById(id);
    }

    @Override public AiSkillDO get(Long id) { return requireSkill(id); }

    @Override
    public PageResult<AiSkillDO> page(AiSkillPageReqVO req) {
        ensureCreatorSkillWithVersionQuietly();
        return skillMapper.selectPage(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersion(AiSkillVersionSaveReqVO req) {
        AiSkillDO skill = lockSkill(req.getSkillId());
        AiSkillDO creator = ensureCreatorSkill();
        AiSkillVersionDO creatorVer = ensureCreatorVersion(creator);
        int creatorVerNo = creatorVer.getVersion() == null ? 1 : creatorVer.getVersion();

        if (StrUtil.isBlank(req.getConfirmationPolicy())) {
            req.setConfirmationPolicy(isReadOnlyRisk(skill.getRiskLevel()) ? "NONE" : "REQUIRED");
        }
        if (StrUtil.isBlank(req.getInputSchema())) {
            req.setInputSchema("{\"type\":\"object\"}");
        }
        if (StrUtil.isBlank(req.getOutputTemplate())) {
            req.setOutputTemplate(buildDefaultOutputTemplate(skill.getRiskLevel()));
        }
        req.setChangeLog(buildCreatorChangeLog(req.getChangeLog(), creatorVerNo));
        validateVersionCompliance(skill, req);
        validateContent(req);
        require(req.getPublishStatus() == null || "DRAFT".equals(req.getPublishStatus()), "新版本必须为草稿");
        int next = versionMapper.selectListBySkillId(req.getSkillId()).stream()
                .map(AiSkillVersionDO::getVersion).max(Integer::compareTo).orElse(0) + 1;
        require(req.getVersion() == null || req.getVersion() == next, "版本号由服务端递增分配");
        AiSkillVersionDO version = BeanUtils.toBean(req, AiSkillVersionDO.class)
                .setId(null).setVersion(next).setRevision(0L).setPublishStatus("DRAFT").setPublishedAt(null);
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
        AiSkillDO skill = skillMapper.selectById(version.getSkillId());
        validateVersionCompliance(skill, req);
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
        AiSkillDO skill = requireSkill(version.getSkillId());
        require(isReadOnlyRisk(skill.getRiskLevel()) || "REQUIRED".equals(version.getConfirmationPolicy()),
                "非只读 Skill 确认策略必须为 REQUIRED");
        validateBindings(skill, version, bindingMapper.selectListByVersionId(id), false);
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
        require(isReadOnlyRisk(skill.getRiskLevel()) || "REQUIRED".equals(version.getConfirmationPolicy()),
                "非只读 Skill 确认策略必须为 REQUIRED");
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
        AiSkillDO skill = requireSkill(version.getSkillId());
        List<AiSkillBindingDO> bindings = prepareBindings(skill, version, requests);
        bindingMapper.delete(new LambdaQueryWrapper<AiSkillBindingDO>().eq(AiSkillBindingDO::getSkillVersionId, id));
        for (AiSkillBindingDO binding : bindings) {
            binding.setSkillVersionId(id);
            bindingMapper.insert(binding);
        }
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

    @Override
    public AiSkillCreatorOptionsRespVO getCreatorOptions() {
        AiSkillDO creator = ensureCreatorSkill();
        AiSkillVersionDO creatorVersion = ensureCreatorVersion(creator);
        AiSkillRespVO creatorResp = BeanUtils.toBean(creator, AiSkillRespVO.class);
        creatorResp.setBuiltIn(true);
        creatorResp.setCreatorSkillCode(BUILT_IN_CREATOR_CODE);

        List<AiSkillCreatorOptionsRespVO.LocalToolOption> localTools = new ArrayList<>();
        if (toolMapper != null) {
            List<AiToolDO> tools = toolMapper.selectList(new LambdaQueryWrapper<AiToolDO>()
                    .eq(AiToolDO::getStatus, 0).orderByAsc(AiToolDO::getId));
            for (AiToolDO tool : tools == null ? List.<AiToolDO>of() : tools) {
                String risk = resolveLocalToolRisk(tool.getName());
                if (risk == null || !RISKS.contains(risk)) continue;
                var option = new AiSkillCreatorOptionsRespVO.LocalToolOption();
                option.setName(tool.getName());
                option.setDescription(tool.getDescription());
                option.setRiskLevel(risk);
                localTools.add(option);
            }
        }

        Map<Long, String> serverNames = new LinkedHashMap<>();
        List<AiSkillCreatorOptionsRespVO.McpServerOption> mcpServers = new ArrayList<>();
        if (serverMapper != null) {
            List<AiMcpServerDO> servers = serverMapper.selectList(new LambdaQueryWrapper<AiMcpServerDO>()
                    .eq(AiMcpServerDO::getStatus, 0).orderByAsc(AiMcpServerDO::getId));
            for (AiMcpServerDO server : servers == null ? List.<AiMcpServerDO>of() : servers) {
                serverNames.put(server.getId(), server.getName());
                var option = new AiSkillCreatorOptionsRespVO.McpServerOption();
                option.setId(server.getId());
                option.setCode(server.getCode());
                option.setName(server.getName());
                option.setHealthStatus(server.getHealthStatus());
                mcpServers.add(option);
            }
        }

        List<AiSkillCreatorOptionsRespVO.McpToolOption> mcpTools = new ArrayList<>();
        if (mcpToolMapper != null && !serverNames.isEmpty()) {
            List<AiMcpToolDO> tools = mcpToolMapper.selectList(new LambdaQueryWrapper<AiMcpToolDO>()
                    .eq(AiMcpToolDO::getStatus, 0).orderByAsc(AiMcpToolDO::getId));
            for (AiMcpToolDO tool : tools == null ? List.<AiMcpToolDO>of() : tools) {
                if (!serverNames.containsKey(tool.getServerId())) continue;
                if ("MISSING".equals(tool.getSyncStatus()) || !RISKS.contains(tool.getRiskLevel())) continue;
                var option = new AiSkillCreatorOptionsRespVO.McpToolOption();
                option.setId(tool.getId());
                option.setServerId(tool.getServerId());
                option.setServerName(serverNames.get(tool.getServerId()));
                option.setName(tool.getName());
                option.setTitle(tool.getTitle());
                option.setDescription(tool.getDescription());
                option.setRiskLevel(tool.getRiskLevel());
                mcpTools.add(option);
            }
        }

        List<AiSkillCreatorOptionsRespVO.KnowledgeOption> knowledgeBases = new ArrayList<>();
        if (knowledgeMapper != null) {
            List<AiKnowledgeDO> list = knowledgeMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDO>()
                    .eq(AiKnowledgeDO::getStatus, 0).orderByAsc(AiKnowledgeDO::getId));
            for (AiKnowledgeDO kb : list == null ? List.<AiKnowledgeDO>of() : list) {
                var option = new AiSkillCreatorOptionsRespVO.KnowledgeOption();
                option.setId(kb.getId());
                option.setName(kb.getName());
                option.setDescription(kb.getDescription());
                knowledgeBases.add(option);
            }
        }

        AiSkillCreatorOptionsRespVO resp = new AiSkillCreatorOptionsRespVO();
        resp.setCreatorSkill(creatorResp);
        resp.setCreatorVersion(creatorVersion.getVersion() == null ? 1 : creatorVersion.getVersion());
        resp.setLocalTools(localTools);
        resp.setMcpServers(mcpServers);
        resp.setMcpTools(mcpTools);
        resp.setKnowledgeBases(knowledgeBases);
        resp.setComplianceRules(COMPLIANCE_RULES);
        return resp;
    }

    @Override
    public AiSkillBlueprintRespVO generateBlueprint(AiSkillBlueprintReqVO req) {
        require(req != null && (StrUtil.isNotBlank(req.getName()) || StrUtil.isNotBlank(req.getGoal())),
                "请填写 Skill 名称或业务目标说明");
        String riskLevel = StrUtil.blankToDefault(req.getRiskLevel(), "READ_ONLY");
        require(RISKS.contains(riskLevel), "风险级别无效");
        String category = StrUtil.blankToDefault(req.getCategory(), "CPS");
        String channelScope = StrUtil.blankToDefault(req.getChannelScope(), "ALL");
        String name = StrUtil.isNotBlank(req.getName())
                ? req.getName().trim()
                : StrUtil.maxLength(req.getGoal().trim(), 32);
        String goal = StrUtil.isNotBlank(req.getGoal()) ? req.getGoal().trim() : name;
        String code = normalizeCode(req.getCode(), name, category, channelScope);

        AiSkillDO creator = ensureCreatorSkill();
        AiSkillVersionDO creatorVer = ensureCreatorVersion(creator);
        int creatorVerNo = creatorVer.getVersion() == null ? 1 : creatorVer.getVersion();

        List<AiSkillBindingSaveReqVO> recommendedBindings = recommendBindings(name, goal, channelScope, riskLevel);
        String confirmationPolicy = isReadOnlyRisk(riskLevel) ? "NONE" : "REQUIRED";
        String systemPrompt = buildStandardPrompt(name, goal, channelScope, riskLevel);
        String inputSchema = buildDefaultInputSchema(channelScope);
        String outputTemplate = buildDefaultOutputTemplate(riskLevel);
        String description = StrUtil.maxLength(goal, 200);
        String generationMode = "SKILL_CREATOR_TEMPLATE";

        if (Boolean.TRUE.equals(req.getUseAi()) && modelService != null) {
            try {
                AiModelDO model = req.getModelId() == null
                        ? modelService.getRequiredDefaultModel(AiModelTypeEnum.CHAT.getType())
                        : modelService.validateModel(req.getModelId());
                if (Objects.equals(model.getType(), AiModelTypeEnum.CHAT.getType())) {
                    var options = AiUtils.buildChatOptions(
                            AiPlatformEnum.validatePlatform(model.getPlatform()),
                            model.getModel(),
                            0.2D,
                            Math.min(2048, model.getMaxTokens() == null ? 2048 : model.getMaxTokens()),
                            List.of(),
                            Map.of());
                    String userInstruction = "请基于内置 skill-creator 规范，为以下业务需求生成 JSON 格式的 Skill 草案（仅输出 JSON 对象，包含 description, systemPrompt, inputSchema, outputTemplate 字段）：\n"
                            + "名称：" + name + "\n"
                            + "分类：" + category + "\n"
                            + "适用渠道：" + channelScope + "\n"
                            + "风险等级：" + riskLevel + "\n"
                            + "确认策略：" + confirmationPolicy + "\n"
                            + "业务目标：" + goal;
                    var response = modelService.getChatModel(model.getId()).call(new Prompt(
                            List.of(new SystemMessage(creatorVer.getSystemPrompt()), new UserMessage(userInstruction)),
                            options));
                    String content = AiUtils.getChatResponseContent(response);
                    JSONObject parsed = parseJsonObjectQuietly(content);
                    if (parsed != null) {
                        if (StrUtil.isNotBlank(parsed.getStr("systemPrompt"))) {
                            systemPrompt = ensureCompliantPrompt(parsed.getStr("systemPrompt"), riskLevel);
                        }
                        if (StrUtil.isNotBlank(parsed.getStr("outputTemplate"))) {
                            outputTemplate = parsed.getStr("outputTemplate").trim();
                        }
                        if (StrUtil.isNotBlank(parsed.getStr("description"))) {
                            description = StrUtil.maxLength(parsed.getStr("description").trim(), 200);
                        }
                        Object schemaCandidate = parsed.get("inputSchema");
                        String schemaStr = schemaCandidate instanceof String s ? s : JSONUtil.toJsonStr(schemaCandidate);
                        if (isValidInputSchema(schemaStr)) {
                            inputSchema = schemaStr;
                        }
                        generationMode = "SKILL_CREATOR_AI";
                    }
                }
            } catch (RuntimeException ignored) {
                // Fallback to deterministic skill-creator template when AI model is unavailable.
            }
        }

        AiSkillBlueprintRespVO resp = new AiSkillBlueprintRespVO();
        resp.setCreatorSkillCode(BUILT_IN_CREATOR_CODE);
        resp.setCreatorSkillVersion(creatorVerNo);
        resp.setGenerationMode(generationMode);
        resp.setCode(code);
        resp.setName(name);
        resp.setCategory(category);
        resp.setChannelScope(channelScope);
        resp.setDescription(description);
        resp.setRiskLevel(riskLevel);
        resp.setSystemPrompt(systemPrompt);
        resp.setInputSchema(inputSchema);
        resp.setOutputTemplate(outputTemplate);
        resp.setConfirmationPolicy(confirmationPolicy);
        resp.setChangeLog(buildCreatorChangeLog(null, creatorVerNo));
        resp.setBindings(recommendedBindings);
        resp.setComplianceRules(COMPLIANCE_RULES);
        return resp;
    }

    private AiSkillDO ensureCreatorSkill() {
        if (skillMapper == null) {
            return new AiSkillDO().setId(1L).setCode(BUILT_IN_CREATOR_CODE).setName(CREATOR_NAME)
                    .setCategory("SYSTEM").setChannelScope("ALL").setDescription(CREATOR_DESCRIPTION)
                    .setRiskLevel("READ_ONLY").setStatus(0).setCurrentVersion(1);
        }
        AiSkillDO creator = skillMapper.selectOne(AiSkillDO::getCode, BUILT_IN_CREATOR_CODE);
        if (creator == null) {
            creator = new AiSkillDO()
                    .setCode(BUILT_IN_CREATOR_CODE)
                    .setName(CREATOR_NAME)
                    .setCategory("SYSTEM")
                    .setChannelScope("ALL")
                    .setDescription(CREATOR_DESCRIPTION)
                    .setRiskLevel("READ_ONLY")
                    .setStatus(0)
                    .setCurrentVersion(1);
            skillMapper.insert(creator);
        } else if (!Integer.valueOf(0).equals(creator.getStatus())
                || creator.getCurrentVersion() == null || creator.getCurrentVersion() < 1) {
            creator.setStatus(0);
            if (creator.getCurrentVersion() == null || creator.getCurrentVersion() < 1) {
                creator.setCurrentVersion(1);
            }
            skillMapper.updateById(new AiSkillDO().setId(creator.getId())
                    .setStatus(0).setCurrentVersion(creator.getCurrentVersion()));
        }
        return creator;
    }

    private AiSkillVersionDO ensureCreatorVersion(AiSkillDO creator) {
        int verNo = creator.getCurrentVersion() == null || creator.getCurrentVersion() < 1 ? 1 : creator.getCurrentVersion();
        if (versionMapper == null || creator.getId() == null) {
            return new AiSkillVersionDO().setSkillId(creator.getId()).setVersion(verNo).setRevision(0L)
                    .setSystemPrompt(CREATOR_SYSTEM_PROMPT).setInputSchema(CREATOR_INPUT_SCHEMA)
                    .setOutputTemplate(CREATOR_OUTPUT_TEMPLATE).setConfirmationPolicy("NONE")
                    .setPublishStatus("PUBLISHED").setChangeLog("系统内置 Skill 创建器基线版本");
        }
        AiSkillVersionDO version = versionMapper.selectBySkillIdAndVersion(creator.getId(), verNo);
        if (version == null) {
            version = new AiSkillVersionDO()
                    .setSkillId(creator.getId())
                    .setVersion(verNo)
                    .setRevision(0L)
                    .setSystemPrompt(CREATOR_SYSTEM_PROMPT)
                    .setInputSchema(CREATOR_INPUT_SCHEMA)
                    .setOutputTemplate(CREATOR_OUTPUT_TEMPLATE)
                    .setConfirmationPolicy("NONE")
                    .setPublishStatus("PUBLISHED")
                    .setChangeLog("系统内置 Skill 创建器基线版本")
                    .setPublishedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            versionMapper.insert(version);
        }
        return version;
    }

    private void ensureCreatorSkillWithVersionQuietly() {
        try {
            AiSkillDO creator = ensureCreatorSkill();
            ensureCreatorVersion(creator);
        } catch (RuntimeException ignored) {
        }
    }

    private List<AiSkillBindingDO> prepareBindings(AiSkillDO skill, AiSkillVersionDO version, List<AiSkillBindingSaveReqVO> requests) {
        if (requests == null || requests.isEmpty()) return List.of();
        require(requests.size() <= 100, "绑定数量不能超过 100");
        List<AiSkillBindingDO> bindings = new ArrayList<>();
        for (AiSkillBindingSaveReqVO req : requests) {
            require(req != null, "绑定不能为空");
            AiSkillBindingDO binding = BeanUtils.toBean(req, AiSkillBindingDO.class).setId(null)
                    .setSkillVersionId(version.getId());
            if (binding.getEnabled() == null) binding.setEnabled(1);
            require(binding.getEnabled() == 0 || binding.getEnabled() == 1, "绑定启用值无效");
            bindings.add(binding);
        }
        validateBindings(skill, version, bindings, false);
        return bindings;
    }

    private List<AiSkillBindingSaveReqVO> recommendBindings(String name, String goal, String channelScope, String riskLevel) {
        if (toolMapper == null) return List.of();
        List<AiToolDO> allTools = toolMapper.selectList(new LambdaQueryWrapper<AiToolDO>()
                .eq(AiToolDO::getStatus, 0).orderByAsc(AiToolDO::getId));
        if (allTools == null || allTools.isEmpty()) return List.of();

        String text = (name + " " + goal + " " + channelScope).toLowerCase(Locale.ROOT);
        int maxRiskIdx = RISKS.indexOf(riskLevel);
        Map<String, AiToolDO> allowedTools = new LinkedHashMap<>();
        for (AiToolDO tool : allTools) {
            String toolRisk = resolveLocalToolRisk(tool.getName());
            if (toolRisk != null && RISKS.contains(toolRisk) && RISKS.indexOf(toolRisk) <= maxRiskIdx) {
                allowedTools.put(tool.getName(), tool);
            }
        }

        Set<String> matched = new LinkedHashSet<>();
        if (containsAny(text, "搜索", "选品", "商品", "带货", "推荐", "直播", "短视频")) {
            addIfPresent(matched, allowedTools, "cps_search_goods");
        }
        if (containsAny(text, "比价", "对比", "价格", "优惠", "选品")) {
            addIfPresent(matched, allowedTools, "cps_compare_prices");
        }
        if (containsAny(text, "共振", "热销", "爆款", "榜单")) {
            addIfPresent(matched, allowedTools, "cps_find_resonance_goods");
        }
        if (containsAny(text, "替代", "平替", "高佣替代")) {
            addIfPresent(matched, allowedTools, "cps_find_alternatives");
        }
        if (containsAny(text, "详情", "深度分析", "价格带", "分析")) {
            addIfPresent(matched, allowedTools, "cps_analyze_goods_detail");
        }
        if (containsAny(text, "主题", "专题", "选品库")) {
            addIfPresent(matched, allowedTools, "cps_list_selection_themes");
        }
        if (containsAny(text, "投放", "策略", "带货", "抖音", "视频号", "话术", "推广")) {
            addIfPresent(matched, allowedTools, "cps_promotion_strategy_advice");
        }
        if (containsAny(text, "订单", "明细", "进度")) {
            addIfPresent(matched, allowedTools, "cps_query_orders");
        }
        if (containsAny(text, "画像", "复盘")) {
            addIfPresent(matched, allowedTools, "cps_analyze_order_profile");
        }
        if (containsAny(text, "趋势", "客单价")) {
            addIfPresent(matched, allowedTools, "cps_analyze_order_trend");
        }
        if (containsAny(text, "返利", "佣金", "余额", "结算")) {
            addIfPresent(matched, allowedTools, "cps_get_rebate_summary");
            addIfPresent(matched, allowedTools, "cps_get_rebate_balance");
        }
        if (containsAny(text, "转链", "链接", "口令", "推广链接", "带货", "投放")) {
            addIfPresent(matched, allowedTools, "cps_generate_link");
        }
        if (containsAny(text, "兑换", "token")) {
            addIfPresent(matched, allowedTools, "cps_query_exchange_status");
            addIfPresent(matched, allowedTools, "cps_create_token_exchange");
        }
        if (containsAny(text, "任务", "cpx")) {
            addIfPresent(matched, allowedTools, "cpx_list_tasks");
        }
        if (matched.isEmpty()) {
            addIfPresent(matched, allowedTools, "cps_search_goods");
            addIfPresent(matched, allowedTools, "cps_compare_prices");
        }

        List<AiSkillBindingSaveReqVO> result = new ArrayList<>();
        for (String toolName : matched) {
            AiSkillBindingSaveReqVO binding = new AiSkillBindingSaveReqVO();
            binding.setBindingType("LOCAL_TOOL");
            binding.setLocalToolName(toolName);
            binding.setEnabled(1);
            result.add(binding);
        }
        return result;
    }

    private static void addIfPresent(Set<String> target, Map<String, AiToolDO> allowed, String toolName) {
        if (allowed.containsKey(toolName)) target.add(toolName);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private static String normalizeCode(String code, String name, String category, String channelScope) {
        if (StrUtil.isNotBlank(code)) {
            String trimmed = code.trim();
            require(trimmed.matches("[a-zA-Z0-9_-]{1,64}"), "编码格式无效");
            return trimmed;
        }
        String prefix = StrUtil.blankToDefault(category, "cps").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String scope = StrUtil.blankToDefault(channelScope, "all").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        String asciiFromName = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        String suffix = StrUtil.isNotBlank(asciiFromName)
                ? asciiFromName
                : Integer.toHexString(Math.abs(Objects.hash(name, category, channelScope)));
        String candidate = (prefix + "-" + scope + "-" + suffix).replaceAll("-+", "-").replaceAll("^-+|-+$", "");
        return StrUtil.maxLength(candidate, 60);
    }

    private static String buildStandardPrompt(String name, String goal, String channelScope, String riskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是【").append(name).append("】能力模块（适用渠道：").append(StrUtil.blankToDefault(channelScope, "ALL")).append("）。");
        sb.append("核心职责：").append(goal).append("。");
        sb.append("必须严格基于当前会话已授权的工具与知识库真实返回结果回答，严禁编造实时商品、价格、优惠券、佣金、销量、订单或返利状态；");
        sb.append("涉及会员订单、返利、推广转链或资产操作时，仅使用系统可信上下文身份，严禁采信消息中的 memberId 或 userId；");
        if (!isReadOnlyRisk(riskLevel)) {
            sb.append("生成推广链接或执行任何写入/变更操作前，必须先向用户说明操作影响并获得明确确认；");
        }
        sb.append("面向用户输出业务结论与依据，不得暴露内部工具编码（如 cps_*、cpx_*）或技术密钥。");
        return sb.toString();
    }

    private static String ensureCompliantPrompt(String prompt, String riskLevel) {
        String base = prompt == null ? "" : prompt.trim();
        StringBuilder sb = new StringBuilder(base);
        if (!base.contains("编造")) {
            sb.append(" 仅依据已授权工具与知识库真实结果回答，严禁编造实时数据。");
        }
        if (!base.contains("memberId") && !base.contains("可信")) {
            sb.append(" 仅使用系统可信身份上下文，拒绝采信消息中的 memberId 或 userId。");
        }
        if (!isReadOnlyRisk(riskLevel) && !base.contains("确认")) {
            sb.append(" 执行转链或写操作前必须要求用户明确确认。");
        }
        return sb.toString().trim();
    }

    private static String buildDefaultInputSchema(String channelScope) {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\",\"description\":\"用户输入或业务诉求\"},\"channel\":{\"type\":\"string\",\"description\":\"目标渠道（默认 "
                + StrUtil.blankToDefault(channelScope, "ALL") + "）\"}},\"required\":[\"query\"]}";
    }

    private static String buildDefaultOutputTemplate(String riskLevel) {
        if (isReadOnlyRisk(riskLevel)) {
            return "返回业务结论、真实工具/知识库数据依据、核心指标对比与风险提示；不暴露内部工具编码。";
        }
        return "返回业务分析结论、真实数据依据、待执行操作摘要及用户确认提示；未确认前不执行写操作，不暴露内部工具编码。";
    }

    private static String buildCreatorChangeLog(String customLog, int creatorVerNo) {
        String prefix = "基于内置 " + BUILT_IN_CREATOR_CODE + " v" + creatorVerNo + " 创建";
        if (StrUtil.isBlank(customLog)) {
            return prefix;
        }
        String trimmed = customLog.trim();
        return trimmed.contains(BUILT_IN_CREATOR_CODE) ? trimmed : prefix + "：" + trimmed;
    }

    private static boolean isReadOnlyRisk(String riskLevel) {
        return riskLevel == null || "READ_ONLY".equals(riskLevel);
    }

    private void validateVersionCompliance(AiSkillDO skill, AiSkillVersionSaveReqVO req) {
        if (skill != null && !isReadOnlyRisk(skill.getRiskLevel())) {
            require("REQUIRED".equals(req.getConfirmationPolicy()), "非只读 Skill 确认策略必须为 REQUIRED");
        }
    }

    private static boolean isValidInputSchema(String schema) {
        if (StrUtil.isBlank(schema) || schema.length() > 16000) return false;
        try {
            return JSONUtil.parseObj(schema).containsKey("type");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static JSONObject parseJsonObjectQuietly(String text) {
        if (StrUtil.isBlank(text)) return null;
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        try {
            return JSONUtil.parseObj(trimmed.substring(start, end + 1));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolveLocalToolRisk(String toolName) {
        if (StrUtil.isBlank(toolName) || riskProviders == null) return null;
        return riskProviders.stream()
                .map(provider -> provider.riskOf(toolName))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(RISKS::indexOf))
                .orElse(null);
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
                String risk = resolveLocalToolRisk(binding.getLocalToolName());
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
        if (StrUtil.isNotBlank(req.getOutputTemplate())) {
            require(req.getOutputTemplate().length() <= 8000, "输出模板不能超过 8000 字符");
        }
        if (StrUtil.isNotBlank(req.getInputSchema())) {
            require(req.getInputSchema().length() <= 16000, "输入契约过长");
            try { require(JSONUtil.parseObj(req.getInputSchema()).containsKey("type"), "输入契约需声明 type"); }
            catch (cn.hutool.json.JSONException ex) { throw exception(INVALID, "输入契约必须是 JSON 对象"); }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw exception(INVALID, message); }
}
