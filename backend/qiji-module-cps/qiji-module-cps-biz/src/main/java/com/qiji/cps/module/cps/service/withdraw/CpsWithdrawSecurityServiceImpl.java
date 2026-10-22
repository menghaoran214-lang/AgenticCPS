package com.qiji.cps.module.cps.service.withdraw;

import cn.hutool.core.util.StrUtil;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawCreateReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawDO;
import com.qiji.cps.module.cps.dal.mysql.withdraw.CpsWithdrawMapper;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import com.qiji.cps.module.system.api.sms.SmsCodeApi;
import com.qiji.cps.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.qiji.cps.module.system.enums.sms.SmsSceneEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.qiji.cps.module.cps.enums.CpsErrorCodeConstants.*;
import static com.qiji.cps.module.member.enums.ErrorCodeConstants.USER_MOBILE_NOT_EXISTS;

/**
 * CPS 提现安全风控 Service 实现类
 *
 * @author CPS System
 */
@Service
@Validated
@Slf4j
public class CpsWithdrawSecurityServiceImpl implements CpsWithdrawSecurityService {

    /** 单笔最小提现金额：100分（1.00元） */
    public static final long MIN_AMOUNT_CENT = 100L;
    /** 单笔最大提现金额：500,000分（5,000.00元） */
    public static final long MAX_SINGLE_AMOUNT_CENT = 500_000L;
    /** 单日最多提现次数：3次 */
    public static final int MAX_DAILY_COUNT = 3;
    /** 单日最大提现累计金额：1,000,000分（10,000.00元） */
    public static final long MAX_DAILY_AMOUNT_CENT = 1_000_000L;
    /** 小额免审自动打款上限：20,000分（200.00元） */
    public static final long AUTO_APPROVE_MAX_CENT = 20_000L;

    @Resource
    private CpsWithdrawMapper withdrawMapper;

    @Resource
    private MemberUserApi memberUserApi;

    @Resource
    private SmsCodeApi smsCodeApi;

    @Override
    public CpsWithdrawSecurityResult checkAndEvaluate(Long memberId, AppCpsWithdrawCreateReqVO request,
                                                      CpsWithdrawAccountDO account, String clientIp) {
        // 1. 确定收款账户及姓名
        String finalType;
        String finalAccount;
        String finalAccountName;
        Long accountId = null;

        if (account != null) {
            finalType = account.getType();
            finalAccount = account.getAccount();
            finalAccountName = account.getAccountName();
            accountId = account.getId();
        } else {
            finalType = request.getWithdrawType();
            finalAccount = request.getWithdrawAccount();
            finalAccountName = request.getWithdrawAccountName();
        }

        if (StrUtil.isBlank(finalType)) {
            throw new IllegalArgumentException("提现类型不能为空");
        }
        if (StrUtil.isBlank(finalAccount)) {
            throw new IllegalArgumentException("提现账号不能为空");
        }
        if (StrUtil.isBlank(finalAccountName)) {
            throw new IllegalArgumentException("收款人姓名不能为空");
        }

        // 2. 实名一致性强校验
        MemberUserRespDTO user = memberUserApi.getUser(memberId);
        if (user != null && StrUtil.isNotBlank(user.getName())) {
            if (!user.getName().trim().equals(finalAccountName.trim())) {
                throw exception(WITHDRAW_NAME_NOT_MATCH_REAL);
            }
        }

        // 3. 单笔限额规则检查
        long amountCent = request.getAmountCent();
        if (amountCent < MIN_AMOUNT_CENT) {
            throw exception(WITHDRAW_AMOUNT_MIN);
        }
        if (amountCent > MAX_SINGLE_AMOUNT_CENT) {
            throw exception(WITHDRAW_AMOUNT_MAX);
        }

        // 4. 当日提现频次与累计金额检查
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        List<CpsWithdrawDO> todayList = withdrawMapper.selectTodayActiveList(memberId, startOfDay, endOfDay);
        if (todayList != null && todayList.size() >= MAX_DAILY_COUNT) {
            throw exception(WITHDRAW_DAILY_LIMIT);
        }
        long todaySum = todayList == null ? 0 : todayList.stream()
                .mapToLong(w -> w.getAmountCent() != null ? w.getAmountCent() : 0L).sum();
        if (todaySum + amountCent > MAX_DAILY_AMOUNT_CENT) {
            throw exception(WITHDRAW_DAILY_AMOUNT_LIMIT);
        }

        // 5. 短信安全校验策略
        boolean requireSms = amountCent > AUTO_APPROVE_MAX_CENT || account == null;
        if (requireSms && StrUtil.isBlank(request.getSmsCode())) {
            throw exception(WITHDRAW_SECURITY_SMS_REQUIRED);
        }
        if (StrUtil.isNotBlank(request.getSmsCode())) {
            if (user == null || StrUtil.isBlank(user.getMobile())) {
                throw exception(USER_MOBILE_NOT_EXISTS);
            }
            smsCodeApi.useSmsCode(new SmsCodeUseReqDTO()
                    .setMobile(user.getMobile())
                    .setScene(SmsSceneEnum.MEMBER_WITHDRAW.getScene())
                    .setCode(request.getSmsCode().trim())
                    .setUsedIp(StrUtil.isNotBlank(clientIp) ? clientIp : "127.0.0.1"));
        }

        // 6. 分级决策：小额已绑默认账户自动出款，其余进人工审核
        boolean canAutoApprove = amountCent <= AUTO_APPROVE_MAX_CENT
                && account != null
                && Boolean.TRUE.equals(account.getIsDefault());

        int riskLevel = canAutoApprove ? 0 : 1;
        String reviewNote = canAutoApprove
                ? "小额常用账户系统免审自动通过"
                : (amountCent > AUTO_APPROVE_MAX_CENT ? "大额提现待人工复审" : "非常用/未绑定账户待人工复审");

        return CpsWithdrawSecurityResult.builder()
                .riskLevel(riskLevel)
                .reviewNote(reviewNote)
                .finalWithdrawType(finalType)
                .finalWithdrawAccount(finalAccount)
                .finalWithdrawAccountName(finalAccountName)
                .accountId(accountId)
                .build();
    }

}
