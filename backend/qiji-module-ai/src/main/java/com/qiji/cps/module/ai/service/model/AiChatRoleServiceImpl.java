package com.qiji.cps.module.ai.service.model;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.qiji.cps.framework.common.enums.CommonStatusEnum;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRolePageReqVO;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleSaveMyReqVO;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleSaveReqVO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.mysql.model.AiChatRoleMapper;
import com.qiji.cps.module.ai.dal.mysql.model.AiChatRoleVersionMapper;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleVersionDO;
import com.qiji.cps.framework.common.util.json.JsonUtils;
import com.qiji.cps.module.ai.service.knowledge.AiKnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.qiji.cps.module.ai.service.capability.AiCapabilitySnapshotService;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.qiji.cps.framework.common.util.collection.CollectionUtils.convertList;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_ROLE_DISABLE;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_ROLE_MEMBER_DISABLED;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_ROLE_MEMBER_NOT_AVAILABLE;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_ROLE_NOT_EXISTS;

/**
 * AI 聊天角色 Service 实现类
 *
 * @author fansili
 */
@Service
@Slf4j
public class AiChatRoleServiceImpl implements AiChatRoleService {

    @Resource
    private AiChatRoleMapper chatRoleMapper;

    @Resource
    private AiChatRoleVersionMapper roleVersionMapper;

    @Resource
    private AiKnowledgeService knowledgeService;
    @Resource
    private AiToolService toolService;
    @Resource
    private AiCapabilitySnapshotService capabilitySnapshots;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChatRole(AiChatRoleSaveReqVO createReqVO) {
        // 校验文档
        validateDocuments(createReqVO.getKnowledgeIds());
        // 校验工具
        validateTools(createReqVO.getToolIds());

        // 保存角色
        AiChatRoleDO chatRole = BeanUtils.toBean(createReqVO, AiChatRoleDO.class).setId(null);
        applyCapabilityDefaults(chatRole);
        chatRole.setVersion(1);
        validatePublishedCapabilities(chatRole);
        chatRoleMapper.insert(chatRole);
        persistVersion(chatRole);
        return chatRole.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createChatRoleMy(AiChatRoleSaveMyReqVO createReqVO, Long userId) {
        // 校验文档
        validateDocuments(createReqVO.getKnowledgeIds());
        // 校验工具
        validateTools(createReqVO.getToolIds());

        // 保存角色
        AiChatRoleDO chatRole = BeanUtils.toBean(createReqVO, AiChatRoleDO.class).setId(null).setVersion(1).setUserId(userId)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setPublicStatus(false);
        applyCapabilityDefaults(chatRole);
        chatRoleMapper.insert(chatRole);
        persistVersion(chatRole);
        return chatRole.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChatRole(AiChatRoleSaveReqVO updateReqVO) {
        // 校验存在
        AiChatRoleDO existing = chatRoleMapper.selectOne(new LambdaQueryWrapperX<AiChatRoleDO>()
                .eq(AiChatRoleDO::getId, updateReqVO.getId()).last("FOR UPDATE"));
        if (existing == null) throw exception(CHAT_ROLE_NOT_EXISTS);
        if (updateReqVO.getVersion() != null && !java.util.Objects.equals(updateReqVO.getVersion(), existing.getVersion()))
            throw new IllegalArgumentException("角色版本已改变，请刷新后重试");
        // 校验文档
        validateDocuments(updateReqVO.getKnowledgeIds());
        // 校验工具
        validateTools(updateReqVO.getToolIds());

        // 更新角色
        AiChatRoleDO updateObj = BeanUtils.toBean(updateReqVO, AiChatRoleDO.class);
        mergeUnsetFields(existing, updateObj);
        if (updateObj.getPublishStatus() == null) updateObj.setPublishStatus(existing.getPublishStatus());
        if (updateObj.getRoleType() == null) updateObj.setRoleType(existing.getRoleType());
        if (updateObj.getSkillIds() == null) updateObj.setSkillIds(existing.getSkillIds());
        if (updateObj.getMcpPolicyId() == null) updateObj.setMcpPolicyId(existing.getMcpPolicyId());
        applyCapabilityDefaults(updateObj);
        updateObj.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
        validatePublishedCapabilities(updateObj);
        chatRoleMapper.updateById(updateObj);
        persistVersion(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateChatRoleMy(AiChatRoleSaveMyReqVO updateReqVO, Long userId) {
        // 校验存在
        AiChatRoleDO chatRole = chatRoleMapper.selectOne(new LambdaQueryWrapperX<AiChatRoleDO>()
                .eq(AiChatRoleDO::getId, updateReqVO.getId()).last("FOR UPDATE"));
        if (chatRole == null) throw exception(CHAT_ROLE_NOT_EXISTS);
        if (ObjectUtil.notEqual(chatRole.getUserId(), userId)) {
            throw exception(CHAT_ROLE_NOT_EXISTS);
        }
        if (updateReqVO.getVersion() != null && !java.util.Objects.equals(updateReqVO.getVersion(),chatRole.getVersion()))
            throw new IllegalArgumentException("角色版本已改变，请刷新后重试");
        // 校验文档
        validateDocuments(updateReqVO.getKnowledgeIds());
        // 校验工具
        validateTools(updateReqVO.getToolIds());

        // 更新
        AiChatRoleDO updateObj = BeanUtils.toBean(updateReqVO, AiChatRoleDO.class);
        mergeUnsetFields(chatRole, updateObj);
        updateObj.setVersion(chatRole.getVersion() == null ? 1 : chatRole.getVersion() + 1);
        chatRoleMapper.updateById(updateObj);
        persistVersion(updateObj);
    }

    /**
     * 校验知识库是否存在
     *
     * @param knowledgeIds 知识库编号列表
     */
    private void validateDocuments(List<Long> knowledgeIds) {
        if (CollUtil.isEmpty(knowledgeIds)) {
            return;
        }
        // 校验文档是否存在
        knowledgeIds.forEach(knowledgeService::validateKnowledgeExists);
    }

    /**
     * 校验工具是否存在
     *
     * @param toolIds 工具编号列表
     */
    private void validateTools(List<Long> toolIds) {
        if (CollUtil.isEmpty(toolIds)) {
            return;
        }
        // 遍历校验每个工具是否存在
        toolIds.forEach(toolService::validateToolExists);
    }

    @Override
    public void deleteChatRole(Long id) {
        // 校验存在
        validateChatRoleExists(id);
        // 删除
        chatRoleMapper.deleteById(id);
    }

    @Override
    public void deleteChatRoleMy(Long id, Long userId) {
        // 校验存在
        AiChatRoleDO chatRole = validateChatRoleExists(id);
        if (ObjectUtil.notEqual(chatRole.getUserId(), userId)) {
            throw exception(CHAT_ROLE_NOT_EXISTS);
        }
        // 删除
        chatRoleMapper.deleteById(id);
    }

    private AiChatRoleDO validateChatRoleExists(Long id) {
        AiChatRoleDO chatRole = chatRoleMapper.selectById(id);
        if (chatRole == null) {
            throw exception(CHAT_ROLE_NOT_EXISTS);
        }
        return chatRole;
    }

    @Override
    public AiChatRoleDO getChatRole(Long id) {
        return chatRoleMapper.selectById(id);
    }

    @Override
    public List<AiChatRoleDO> getChatRoleList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return chatRoleMapper.selectByIds(ids);
    }

    @Override
    public AiChatRoleDO validateChatRole(Long id) {
        AiChatRoleDO chatRole = validateChatRoleExists(id);
        if (CommonStatusEnum.isDisable(chatRole.getStatus())) {
            throw exception(CHAT_ROLE_DISABLE, chatRole.getName());
        }
        return chatRole;
    }

    @Override
    public List<AiChatRoleDO> getMemberEnabledChatRoleList() {
        List<AiChatRoleDO> roles = chatRoleMapper.selectList(new LambdaQueryWrapperX<AiChatRoleDO>()
                .eq(AiChatRoleDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .eq(AiChatRoleDO::getPublicStatus, true)
                .eq(AiChatRoleDO::getMemberEnabled, true)
                .orderByAsc(AiChatRoleDO::getSort));
        return roles.stream().filter(this::isMemberEnabledChatRole).toList();
    }

    @Override
    public AiChatRoleDO getDefaultMemberChatRole() {
        AiChatRoleDO role = CollUtil.getFirst(getMemberEnabledChatRoleList());
        if (role == null) {
            throw exception(CHAT_ROLE_MEMBER_NOT_AVAILABLE);
        }
        return role;
    }

    @Override
    public AiChatRoleDO validateMemberEnabledChatRole(Long id) {
        AiChatRoleDO chatRole = validateChatRoleExists(id);
        if (!isMemberEnabledChatRole(chatRole)) {
            throw exception(CHAT_ROLE_MEMBER_DISABLED);
        }
        return chatRole;
    }

    private boolean isMemberEnabledChatRole(AiChatRoleDO chatRole) {
        return CommonStatusEnum.isEnable(chatRole.getStatus())
                && Boolean.TRUE.equals(chatRole.getPublicStatus())
                && Boolean.TRUE.equals(chatRole.getMemberEnabled())
                && "PUBLISHED".equals(chatRole.getPublishStatus())
                && (capabilitySnapshots == null || capabilitySnapshots.isEnabled()
                || (CollUtil.isEmpty(chatRole.getSkillIds()) && chatRole.getMcpPolicyId() == null));
    }

    @Override
    public PageResult<AiChatRoleDO> getChatRolePage(AiChatRolePageReqVO pageReqVO) {
        return chatRoleMapper.selectPage(pageReqVO);
    }

    @Override
    public PageResult<AiChatRoleDO> getChatRoleMyPage(AiChatRolePageReqVO pageReqVO, Long userId) {
        return chatRoleMapper.selectPageByMy(pageReqVO, userId);
    }

    @Override
    public List<String> getChatRoleCategoryList() {
        List<AiChatRoleDO> list = chatRoleMapper.selectListGroupByCategory(CommonStatusEnum.ENABLE.getStatus());
        return convertList(list, AiChatRoleDO::getCategory,
                role -> role != null && StrUtil.isNotBlank(role.getCategory()));
    }

    @Override
    public List<AiChatRoleDO> getChatRoleListByName(String name) {
        return chatRoleMapper.selectListByName(name);
    }

    @Override
    public List<AiChatRoleDO> getPublishedChatRoleList() {
        return chatRoleMapper.selectList(new LambdaQueryWrapperX<AiChatRoleDO>()
                .eq(AiChatRoleDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .eq(AiChatRoleDO::getPublicStatus, true)
                .eq(AiChatRoleDO::getPublishStatus, "PUBLISHED")
                .orderByAsc(AiChatRoleDO::getSort));
    }

    private void applyCapabilityDefaults(AiChatRoleDO role) {
        if (StrUtil.isBlank(role.getRoleType())) {
            role.setRoleType("CUSTOM");
        }
        if (role.getVersion() == null) {
            role.setVersion(1);
        }
        if (StrUtil.isBlank(role.getPublishStatus())) {
            role.setPublishStatus("DRAFT");
        }
        if (StrUtil.isBlank(role.getRiskLevel())) {
            role.setRiskLevel("READ_ONLY");
        }
        if (role.getAllowMutation() == null) {
            role.setAllowMutation(false);
        }
        if (!List.of("PRESET", "CUSTOM").contains(role.getRoleType())
                || !List.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(role.getPublishStatus())
                || AiCapabilitySnapshotService.rank(role.getRiskLevel()) < 0) {
            throw new IllegalArgumentException("角色类型、发布状态或风险级别无效");
        }
    }

    private void validatePublishedCapabilities(AiChatRoleDO role) {
        if ("PUBLISHED".equals(role.getPublishStatus()) && capabilitySnapshots != null) {
            capabilitySnapshots.validateRole(role);
        }
    }

    private void mergeUnsetFields(AiChatRoleDO source, AiChatRoleDO target) {
        if (target.getModelId() == null) target.setModelId(source.getModelId());
        if (target.getName() == null) target.setName(source.getName());
        if (target.getAvatar() == null) target.setAvatar(source.getAvatar());
        if (target.getCategory() == null) target.setCategory(source.getCategory());
        if (target.getPresetCode() == null) target.setPresetCode(source.getPresetCode());
        if (target.getAudience() == null) target.setAudience(source.getAudience());
        if (target.getChannelScope() == null) target.setChannelScope(source.getChannelScope());
        if (target.getSort() == null) target.setSort(source.getSort());
        if (target.getDescription() == null) target.setDescription(source.getDescription());
        if (target.getSystemMessage() == null) target.setSystemMessage(source.getSystemMessage());
        if (target.getKnowledgeIds() == null) target.setKnowledgeIds(source.getKnowledgeIds());
        if (target.getToolIds() == null) target.setToolIds(source.getToolIds());
        if (target.getMcpClientNames() == null) target.setMcpClientNames(source.getMcpClientNames());
        if (target.getSkillIds() == null) target.setSkillIds(source.getSkillIds());
        if (target.getMcpPolicyId() == null) target.setMcpPolicyId(source.getMcpPolicyId());
        if (target.getRiskLevel() == null) target.setRiskLevel(source.getRiskLevel());
        if (target.getAllowMutation() == null) target.setAllowMutation(source.getAllowMutation());
        if (target.getPublicStatus() == null) target.setPublicStatus(source.getPublicStatus());
        if (target.getMemberEnabled() == null) target.setMemberEnabled(source.getMemberEnabled());
        if (target.getStatus() == null) target.setStatus(source.getStatus());
        if (target.getRoleType() == null) target.setRoleType(source.getRoleType());
        if (target.getPublishStatus() == null) target.setPublishStatus(source.getPublishStatus());
    }

    private void persistVersion(AiChatRoleDO role) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", role.getModelId());
        snapshot.put("name", role.getName()); snapshot.put("avatar", role.getAvatar());
        snapshot.put("category", role.getCategory()); snapshot.put("presetCode", role.getPresetCode());
        snapshot.put("roleType", role.getRoleType()); snapshot.put("audience", role.getAudience());
        snapshot.put("channelScope", role.getChannelScope()); snapshot.put("sort", role.getSort());
        snapshot.put("description", role.getDescription()); snapshot.put("systemMessage", role.getSystemMessage());
        snapshot.put("knowledgeIds", role.getKnowledgeIds()); snapshot.put("toolIds", role.getToolIds());
        snapshot.put("mcpClientNames", role.getMcpClientNames()); snapshot.put("skillIds", role.getSkillIds());
        snapshot.put("mcpPolicyId", role.getMcpPolicyId()); snapshot.put("riskLevel", role.getRiskLevel());
        snapshot.put("allowMutation", role.getAllowMutation()); snapshot.put("publicStatus", role.getPublicStatus());
        snapshot.put("memberEnabled", role.getMemberEnabled()); snapshot.put("status", role.getStatus());
        snapshot.put("publishStatus", role.getPublishStatus());
        roleVersionMapper.insert(new AiChatRoleVersionDO().setRoleId(role.getId()).setVersion(role.getVersion())
                .setPublishStatus(role.getPublishStatus()).setDefinitionSnapshot(JsonUtils.toJsonString(snapshot)));
    }

    @Override
    public List<AiChatRoleVersionDO> getVersionHistory(Long roleId, Long userId) {
        AiChatRoleDO role = validateChatRoleExists(roleId);
        if (!Boolean.TRUE.equals(role.getPublicStatus()) && !java.util.Objects.equals(role.getUserId(), userId))
            throw exception(CHAT_ROLE_NOT_EXISTS);
        return roleVersionMapper.selectHistory(roleId);
    }

}
