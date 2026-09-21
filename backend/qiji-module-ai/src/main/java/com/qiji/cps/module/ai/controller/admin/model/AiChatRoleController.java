package com.qiji.cps.module.ai.controller.admin.model;

import cn.hutool.core.util.ObjUtil;
import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRolePageReqVO;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleRespVO;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleSaveMyReqVO;
import com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleSaveReqVO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.service.model.AiChatRoleService;
import com.fhs.core.trans.anno.TransMethodResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;
import static com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - AI 聊天角色")
@RestController
@RequestMapping("/ai/chat-role")
@Validated
public class AiChatRoleController {

    @Resource
    private AiChatRoleService chatRoleService;

    @GetMapping("/my-page")
    @Operation(summary = "获得【我的】聊天角色分页")
    @TransMethodResult
    public CommonResult<PageResult<AiChatRoleRespVO>> getChatRoleMyPage(@Valid AiChatRolePageReqVO pageReqVO) {
        PageResult<AiChatRoleDO> pageResult = chatRoleService.getChatRoleMyPage(pageReqVO, getLoginUserId());
        return success(BeanUtils.toBean(pageResult, AiChatRoleRespVO.class));
    }

    @GetMapping("/get-my")
    @Operation(summary = "获得【我的】聊天角色")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @TransMethodResult
    public CommonResult<AiChatRoleRespVO> getChatRoleMy(@RequestParam("id") Long id) {
        AiChatRoleDO chatRole = chatRoleService.getChatRole(id);
        if (ObjUtil.notEqual(chatRole.getUserId(), getLoginUserId())) {
            return success(null);
        }
        return success(BeanUtils.toBean(chatRole, AiChatRoleRespVO.class));
    }

    @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)

    @PostMapping("/create-my")
    @Operation(summary = "创建【我的】聊天角色")
    public CommonResult<Long> createChatRoleMy(@Valid @RequestBody AiChatRoleSaveMyReqVO createReqVO) {
        return success(chatRoleService.createChatRoleMy(createReqVO, getLoginUserId()));
    }

    @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)

    @PutMapping("/update-my")
    @Operation(summary = "更新【我的】聊天角色")
    public CommonResult<Boolean> updateChatRoleMy(@Valid @RequestBody AiChatRoleSaveMyReqVO updateReqVO) {
        chatRoleService.updateChatRoleMy(updateReqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete-my")
    @Operation(summary = "删除【我的】聊天角色")
    @Parameter(name = "id", description = "编号", required = true)
    public CommonResult<Boolean> deleteChatRoleMy(@RequestParam("id") Long id) {
        chatRoleService.deleteChatRoleMy(id, getLoginUserId());
        return success(true);
    }

    @GetMapping("/category-list")
    @Operation(summary = "获得聊天角色的分类列表")
    public CommonResult<List<String>> getChatRoleCategoryList() {
        return success(chatRoleService.getChatRoleCategoryList());
    }

    @GetMapping("/published-list")
    @Operation(summary = "获得聊天框可选择的已发布角色")
    public CommonResult<List<AiChatRoleRespVO>> getPublishedChatRoleList() {
        return success(BeanUtils.toBean(chatRoleService.getPublishedChatRoleList(), AiChatRoleRespVO.class));
    }

    /**
     * @deprecated 使用 {@code /published-list}。
     */
    @Deprecated
    @GetMapping("/list")
    @Operation(summary = "兼容旧客户端：获得聊天角色列表")
    public CommonResult<List<AiChatRoleRespVO>> getChatRoleList() {
        return getPublishedChatRoleList();
    }

    /**
     * @deprecated 聊天角色已经统一替代角色预设，客户端应改用 {@code /published-list}。
     */
    @Deprecated
    @GetMapping("/preset-list")
    @Operation(summary = "兼容旧客户端：获得聊天角色列表")
    public CommonResult<List<AiChatRoleRespVO>> getPresetList() {
        return getPublishedChatRoleList();
    }

    @GetMapping("/capability-summary")
    @Operation(summary = "获得角色有效能力摘要")
    public CommonResult<Map<String, Object>> getCapabilitySummary(@RequestParam("roleId") Long roleId) {
        AiChatRoleDO role = chatRoleService.validateChatRole(roleId);
        if (!Boolean.TRUE.equals(role.getPublicStatus())
                && !java.util.Objects.equals(role.getUserId(), getLoginUserId())) {
            throw new IllegalArgumentException("角色不存在或无权使用");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("roleId", role.getId());
        summary.put("presetCode", role.getPresetCode());
        summary.put("version", role.getVersion());
        summary.put("skillIds", role.getSkillIds());
        summary.put("toolIds", role.getToolIds());
        summary.put("mcpClientNames", role.getMcpClientNames());
        summary.put("riskLevel", role.getRiskLevel());
        summary.put("allowMutation", role.getAllowMutation());
        return success(summary);
    }

    // ========== 角色管理 ==========

    @GetMapping("/version/list")
    @Operation(summary = "查询角色版本历史")
    @PreAuthorize("@ss.hasPermission('ai:chat-role:query')")
    public CommonResult<List<com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleVersionRespVO>> versionHistory(
            @RequestParam Long roleId) {
        return success(BeanUtils.toBean(chatRoleService.getVersionHistory(roleId, getLoginUserId()),
                com.qiji.cps.module.ai.controller.admin.model.vo.chatRole.AiChatRoleVersionRespVO.class));
    }

    @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)

    @PostMapping("/create")
    @Operation(summary = "创建聊天角色")
    @PreAuthorize("@ss.hasPermission('ai:chat-role:create')")
    public CommonResult<Long> createChatRole(@Valid @RequestBody AiChatRoleSaveReqVO createReqVO) {
        return success(chatRoleService.createChatRole(createReqVO));
    }

    @com.qiji.cps.framework.apilog.core.annotation.ApiAccessLog(requestEnable = false, responseEnable = false)

    @PutMapping("/update")
    @Operation(summary = "更新聊天角色")
    @PreAuthorize("@ss.hasPermission('ai:chat-role:update')")
    public CommonResult<Boolean> updateChatRole(@Valid @RequestBody AiChatRoleSaveReqVO updateReqVO) {
        chatRoleService.updateChatRole(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除聊天角色")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('ai:chat-role:delete')")
    public CommonResult<Boolean> deleteChatRole(@RequestParam("id") Long id) {
        chatRoleService.deleteChatRole(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得聊天角色")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('ai:chat-role:query')")
    @TransMethodResult
    public CommonResult<AiChatRoleRespVO> getChatRole(@RequestParam("id") Long id) {
        AiChatRoleDO chatRole = chatRoleService.getChatRole(id);
        return success(BeanUtils.toBean(chatRole, AiChatRoleRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得聊天角色分页")
    @PreAuthorize("@ss.hasPermission('ai:chat-role:query')")
    public CommonResult<PageResult<AiChatRoleRespVO>> getChatRolePage(@Valid AiChatRolePageReqVO pageReqVO) {
        PageResult<AiChatRoleDO> pageResult = chatRoleService.getChatRolePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, AiChatRoleRespVO.class));
    }

}
