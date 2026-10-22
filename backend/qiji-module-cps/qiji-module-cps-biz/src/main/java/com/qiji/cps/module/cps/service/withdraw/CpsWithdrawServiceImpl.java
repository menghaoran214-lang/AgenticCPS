package com.qiji.cps.module.cps.service.withdraw;

import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils;
import com.qiji.cps.module.cps.controller.admin.withdraw.vo.CpsWithdrawPageReqVO;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawCreateReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawDO;
import com.qiji.cps.module.cps.dal.mysql.withdraw.CpsWithdrawMapper;
import com.qiji.cps.module.cps.enums.CpsWithdrawStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.qiji.cps.framework.common.util.servlet.ServletUtils.getClientIP;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;
import static com.qiji.cps.module.cps.enums.CpsErrorCodeConstants.WITHDRAW_NOT_EXISTS;
import static com.qiji.cps.module.cps.enums.CpsErrorCodeConstants.WITHDRAW_ACCOUNT_NOT_EXISTS;

/**
 * CPS 提现申请 Service 实现类
 *
 * @author CPS System
 */
@Service
@Validated
@Slf4j
public class CpsWithdrawServiceImpl implements CpsWithdrawService {

    @Resource
    private CpsWithdrawMapper withdrawMapper;
    @Resource
    private CpsWithdrawStepExecutor stepExecutor;
    @Resource
    private CpsWithdrawTransferExecutor transferExecutor;
    @Resource
    private CpsWithdrawAccountService withdrawAccountService;
    @Resource
    private CpsWithdrawSecurityService withdrawSecurityService;

    @Override
    public Long createWithdraw(Long memberId, AppCpsWithdrawCreateReqVO request) {
        if (memberId == null || memberId <= 0) throw new IllegalArgumentException("登录会员不能为空");
        CpsWithdrawDO existing = withdrawMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (existing != null) return validateReplay(existing, memberId, request);

        // 1. 查询绑定账户（若指定）
        CpsWithdrawAccountDO account = null;
        if (request.getAccountId() != null) {
            account = withdrawAccountService.getAccount(memberId, request.getAccountId());
            if (account == null) {
                throw exception(WITHDRAW_ACCOUNT_NOT_EXISTS);
            }
        }

        // 2. 安全风控评估
        String clientIp = getClientIP();
        CpsWithdrawSecurityResult securityResult = withdrawSecurityService.checkAndEvaluate(
                memberId, request, account, clientIp);

        // 3. 构建创建命令
        CpsWithdrawCreateCommand command = new CpsWithdrawCreateCommand(
                memberId, request.getAmountCent(),
                securityResult.getFinalWithdrawType(),
                securityResult.getFinalWithdrawAccount(),
                securityResult.getFinalWithdrawAccountName(),
                request.getIdempotencyKey(),
                securityResult.getAccountId(),
                securityResult.getRiskLevel(),
                clientIp,
                null
        );

        CpsWithdrawDO withdraw;
        try {
            withdraw = stepExecutor.createAndFreeze(command);
        } catch (DuplicateKeyException duplicate) {
            existing = withdrawMapper.selectByIdempotencyKey(request.getIdempotencyKey());
            if (existing == null) throw duplicate;
            return validateReplay(existing, memberId, request);
        }

        // 4. 更新提现账户最近使用时间
        if (securityResult.getAccountId() != null) {
            withdrawAccountService.markAccountUsed(memberId, securityResult.getAccountId());
        }

        // 5. 分级出款决策：低风险小额常用账户系统免审自动打款
        if (Integer.valueOf(0).equals(securityResult.getRiskLevel())) {
            try {
                approveWithdraw(withdraw.getId(), securityResult.getReviewNote());
            } catch (Exception ex) {
                log.warn("[createWithdraw] 自动免审转账触发异常，转入后台补偿: withdrawId={}", withdraw.getId(), ex);
            }
        }

        return withdraw.getId();
    }

    private Long validateReplay(CpsWithdrawDO existing, Long memberId, AppCpsWithdrawCreateReqVO request) {
        boolean accountMatch = (request.getAccountId() != null && Objects.equals(request.getAccountId(), existing.getAccountId()))
                || (request.getWithdrawAccount() != null && Objects.equals(request.getWithdrawAccount(), existing.getWithdrawAccount()));
        if (!memberId.equals(existing.getMemberId()) || !request.getAmountCent().equals(existing.getAmountCent())
                || !accountMatch) {
            throw new IllegalStateException("幂等键已用于其他提现申请");
        }
        return existing.getId();
    }

    @Override
    public PageResult<CpsWithdrawDO> getMemberWithdrawPage(Long memberId, int pageNo, int pageSize) {
        return withdrawMapper.selectMemberPage(memberId, pageNo, pageSize);
    }

    @Override
    public CpsWithdrawDO getMemberWithdraw(Long memberId, Long id) {
        CpsWithdrawDO withdraw = withdrawMapper.selectByMemberIdAndId(memberId, id);
        if (withdraw == null) throw exception(WITHDRAW_NOT_EXISTS);
        return withdraw;
    }

    @Override
    public PageResult<CpsWithdrawDO> getWithdrawPage(CpsWithdrawPageReqVO reqVO) {
        return withdrawMapper.selectPage(reqVO);
    }

    @Override
    public CpsWithdrawDO getWithdraw(Long id) {
        return withdrawMapper.selectById(id);
    }

    @Override
    public void approveWithdraw(Long id, String reviewNote) {
        CpsWithdrawDO withdraw = stepExecutor.markReviewing(id, reviewNote,
                SecurityFrameworkUtils.getLoginUserId());
        if (withdraw != null && CpsWithdrawStatusEnum.REVIEWING.getStatus().equals(withdraw.getStatus())) {
            transferExecutor.startTransfer(id);
        }
    }

    @Override
    public void rejectWithdraw(Long id, String reviewNote) {
        stepExecutor.rejectAndUnfreeze(id, reviewNote, "withdraw-reject:" + id);
    }

}
