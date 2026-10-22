package com.qiji.cps.module.cps.service.withdraw;

import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountRespVO;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;

import java.util.List;

/**
 * 会员提现账户 Service 接口
 *
 * @author CPS System
 */
public interface CpsWithdrawAccountService {

    /**
     * 获取会员提现账户列表
     *
     * @param memberId 会员编号
     * @return 提现账户列表
     */
    List<AppCpsWithdrawAccountRespVO> getMemberAccountList(Long memberId);

    /**
     * 获取提现账户实体
     *
     * @param memberId 会员编号
     * @param id 账户编号
     * @return 账户实体
     */
    CpsWithdrawAccountDO getAccount(Long memberId, Long id);

    /**
     * 保存（新增或修改）提现账户
     *
     * @param memberId 会员编号
     * @param reqVO 保存请求
     * @return 账户编号
     */
    Long saveAccount(Long memberId, AppCpsWithdrawAccountSaveReqVO reqVO);

    /**
     * 设置默认提现账户
     *
     * @param memberId 会员编号
     * @param id 账户编号
     */
    void setDefaultAccount(Long memberId, Long id);

    /**
     * 删除提现账户
     *
     * @param memberId 会员编号
     * @param id 账户编号
     */
    void deleteAccount(Long memberId, Long id);

    /**
     * 标记账户最近使用时间
     *
     * @param memberId 会员编号
     * @param id 账户编号
     */
    void markAccountUsed(Long memberId, Long id);

}
