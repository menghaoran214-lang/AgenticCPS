package com.qiji.cps.module.ai.dal.dataobject.model;

import com.qiji.cps.framework.common.enums.CommonStatusEnum;
import com.qiji.cps.framework.mybatis.core.dataobject.BaseDO;
import com.qiji.cps.framework.mybatis.core.type.LongListTypeHandler;
import com.qiji.cps.framework.mybatis.core.type.StringListTypeHandler;
import com.qiji.cps.module.ai.dal.dataobject.knowledge.AiKnowledgeDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.util.List;

/**
 * AI 聊天角色 DO
 *
 * @author fansili
 * @since 2024/4/24 19:39
 */
@TableName(value = "ai_chat_role", autoResultMap = true)
@KeySequence("ai_chat_role_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRoleDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 角色名称
     */
    private String name;
    /**
     * 角色头像
     */
    private String avatar;
    /**
     * 角色分类
     */
    private String category;
    /** 系统预设编码；自定义角色为空 */
    private String presetCode;
    /** 角色类型（PRESET/CUSTOM） */
    private String roleType;
    /** 适用用户类型（ADMIN/MEMBER/MERCHANT） */
    private String audience;
    /** 适用渠道（ALL/WECHAT_VIDEO/DOUYIN/MALL） */
    private String channelScope;
    /**
     * 角色描述
     */
    private String description;
    /**
     * 角色设定
     */
    private String systemMessage;

    /**
     * 用户编号
     *
     * 关联 AdminUserDO 的 userId 字段
     */
    private Long userId;

    /**
     * 模型编号
     *
     * 关联 {@link AiModelDO#getId()} 字段
     */
    private Long modelId;

    /**
     * 引用的知识库编号列表
     *
     * 关联 {@link AiKnowledgeDO#getId()} 字段
     */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> knowledgeIds;
    /**
     * 引用的工具编号列表
     *
     * 关联 {@link AiToolDO#getId()} 字段
     */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> toolIds;
    /**
     * 引用的 MCP Client 名字列表
     *
     * 关联 spring.ai.mcp.client 下的名字
     */
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> mcpClientNames;
    /** 绑定的 Skill 编号列表 */
    @TableField(typeHandler = LongListTypeHandler.class)
    private List<Long> skillIds;
    /** MCP 权限策略编号 */
    private Long mcpPolicyId;
    /** 风险级别（READ_ONLY/ATTRIBUTION_WRITE/ASSET_WRITE/ADMIN） */
    private String riskLevel;
    /** 角色版本 */
    private Integer version;
    /** 发布状态（DRAFT/PUBLISHED/ARCHIVED） */
    private String publishStatus;
    /** 是否允许执行变更类工具 */
    private Boolean allowMutation;

    /**
     * 是否公开
     *
     * 1. true - 公开；由管理员在【角色管理】所创建
     * 2. false - 私有；由个人在【我的角色】所创建
     */
    private Boolean publicStatus;
    /**
     * 是否允许会员使用
     */
    private Boolean memberEnabled;

    /**
     * 排序值
     */
    private Integer sort;
    /**
     * 状态
     *
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

}
