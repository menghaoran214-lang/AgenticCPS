package com.qiji.cps.module.cps.dal.dataobject.publish;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * CPS 投放任务 DO. 对应「群发 / 定时群发 / 定时消息」投放调度能力。
 */
@TableName("cps_publish_task")
@KeySequence("cps_publish_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsPublishTaskDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 任务编码，全局唯一，便于幂等与外部引用。 */
    private String taskCode;

    /** 任务名称。 */
    private String taskName;

    /** 投放内容来源：MANUAL 手工 / SELECTION_THEME 选品主题 / REBATE_ACTIVITY 活动中心 / CPX_TASK 投放任务。 */
    private String sourceType;

    /** 投放内容来源主键。 */
    private Long sourceId;

    /** 触达渠道：SMS 短信 / NOTIFY 站内信 / CLIPBOARD 文案看板。 */
    private String channelCode;

    /** 目标人群类型：SPECIFIED 指定会员 / REBATE_MEMBER 返利会员。 */
    private String targetType;

    /** 指定会员 ID，逗号分隔。 */
    private String targetIds;

    /** 消息模板，占位符 {member_id}、{task_name}、{url}。 */
    private String contentTemplate;

    /** 推广链接，必须是完整 HTTP(S) 地址，不接受 {member_id} 等归因占位符。 */
    private String linkTemplate;

    /** 渠道模板编码，短信 / 站内信模板使用。 */
    private String templateCode;

    /** Cron 表达式，Spring 格式（秒 分 时 日 月 周），为空表示只手工触发。 */
    private String cronExpression;

    /** 投放开始时间。 */
    private LocalDateTime startTime;

    /** 投放结束时间。 */
    private LocalDateTime endTime;

    /** 每个会员每日最大投放次数，0 表示不限制。 */
    private Integer frequencyLimitPerDay;

    /** 免打扰开始时间。 */
    private LocalTime quietStartTime;

    /** 免打扰结束时间。 */
    private LocalTime quietEndTime;

    /** 任务状态：DRAFT 草稿 / ONLINE 已上线 / OFFLINE 已下线。 */
    private String status;

    /** 上次投放时间。 */
    private LocalDateTime lastDispatchTime;

    /** 下次投放时间。 */
    private LocalDateTime nextDispatchTime;

    private String remark;

}
