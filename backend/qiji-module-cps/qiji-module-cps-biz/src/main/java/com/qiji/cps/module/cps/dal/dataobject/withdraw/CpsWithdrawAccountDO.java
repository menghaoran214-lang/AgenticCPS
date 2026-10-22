package com.qiji.cps.module.cps.dal.dataobject.withdraw;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.qiji.cps.framework.tenant.core.db.TenantBaseDO;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 会员提现账户管理 DO
 *
 * @author CPS System
 */
@TableName("cps_withdraw_account")
@KeySequence("cps_withdraw_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsWithdrawAccountDO extends TenantBaseDO {

    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 会员用户ID
     */
    private Long memberId;

    /**
     * 账户类型（alipay:支付宝 wechat:微信 bank:银行卡）
     */
    private String type;

    /**
     * 提现账号（支付宝账号/微信OpenID/银行卡号）
     */
    private String account;

    /**
     * 真实姓名（必须与收款实名一致）
     */
    private String accountName;

    /**
     * 开户行（银行卡时有效）
     */
    private String bankName;

    /**
     * 是否默认账户（false:否 true:是）
     */
    private Boolean isDefault;

    /**
     * 状态（0:正常 1:禁用）
     */
    private Integer status;

    /**
     * 最近使用时间
     */
    private LocalDateTime lastUsedTime;

}
