package com.qiji.cps.module.cps.service.withdraw;

import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawCreateReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;

/**
 * CPS 提现安全风控 Service 接口
 *
 * @author CPS System
 */
public interface CpsWithdrawSecurityService {

    /**
     * 校验提现安全与限额，并进行风控决策
     *
     * @param memberId 会员编号
     * @param request 提现请求
     * @param account 提现账户（可为 null）
     * @param clientIp 客户端IP
     * @return 风控评估结果
     */
    CpsWithdrawSecurityResult checkAndEvaluate(Long memberId, AppCpsWithdrawCreateReqVO request,
                                               CpsWithdrawAccountDO account, String clientIp);

}
