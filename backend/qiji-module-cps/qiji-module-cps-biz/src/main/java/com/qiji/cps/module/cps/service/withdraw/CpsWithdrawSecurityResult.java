package com.qiji.cps.module.cps.service.withdraw;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CPS 提现风控与安全评估结果
 *
 * @author CPS System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CpsWithdrawSecurityResult {

    /**
     * 风控评级：0 正常自动出款，1 待人工审核
     */
    private Integer riskLevel;

    /**
     * 审核/风控说明
     */
    private String reviewNote;

    /**
     * 最终确定的提现类型
     */
    private String finalWithdrawType;

    /**
     * 最终确定的提现账号
     */
    private String finalWithdrawAccount;

    /**
     * 最终确定的提现真实姓名
     */
    private String finalWithdrawAccountName;

    /**
     * 关联的提现账户ID
     */
    private Long accountId;

}
