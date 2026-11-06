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

/**
 * CPS 投放记录 DO. 记录每一次对单个会员的投放结果，用于频控、幂等与效果追溯。
 */
@TableName("cps_publish_log")
@KeySequence("cps_publish_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsPublishLogDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 投放任务 ID。 */
    private Long taskId;

    /** 投放任务编码。 */
    private String taskCode;

    /** 触达渠道。 */
    private String channelCode;

    /** 目标会员 ID。 */
    private Long memberId;

    /** 幂等键：任务 + 会员 + 投放分钟，同一分钟内重复触发不会重复发送。 */
    private String idempotentKey;

    /** 实际发送内容快照。 */
    private String contentSnapshot;

    /** 实际使用的推广链接。 */
    private String linkUrl;

    /** 投放结果：SUCCESS 成功 / FAILED 失败 / SKIPPED 跳过。 */
    private String status;

    /** 失败原因或跳过原因。 */
    private String errorMsg;

    /** 投放时间。 */
    private LocalDateTime sentAt;

}
