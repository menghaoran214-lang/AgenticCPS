package com.qiji.cps.module.cps.service.withdraw;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountRespVO;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;
import com.qiji.cps.module.cps.dal.mysql.withdraw.CpsWithdrawAccountMapper;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import com.qiji.cps.module.system.api.sms.SmsCodeApi;
import com.qiji.cps.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.qiji.cps.module.system.enums.sms.SmsSceneEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.qiji.cps.framework.common.util.servlet.ServletUtils.getClientIP;
import static com.qiji.cps.module.cps.enums.CpsErrorCodeConstants.*;
import static com.qiji.cps.module.member.enums.ErrorCodeConstants.USER_MOBILE_NOT_EXISTS;

/**
 * 会员提现账户 Service 实现类
 *
 * @author CPS System
 */
@Service
@Validated
@Slf4j
public class CpsWithdrawAccountServiceImpl implements CpsWithdrawAccountService {

    @Resource
    private CpsWithdrawAccountMapper accountMapper;

    @Resource
    private MemberUserApi memberUserApi;

    @Resource
    private SmsCodeApi smsCodeApi;

    @Override
    public List<AppCpsWithdrawAccountRespVO> getMemberAccountList(Long memberId) {
        if (memberId == null || memberId <= 0) {
            return Collections.emptyList();
        }
        List<CpsWithdrawAccountDO> list = accountMapper.selectListByMemberId(memberId);
        return list.stream().map(this::convertToRespVO).collect(Collectors.toList());
    }

    @Override
    public CpsWithdrawAccountDO getAccount(Long memberId, Long id) {
        if (memberId == null || id == null) {
            return null;
        }
        return accountMapper.selectByMemberIdAndId(memberId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAccount(Long memberId, AppCpsWithdrawAccountSaveReqVO reqVO) {
        if (memberId == null || memberId <= 0) {
            throw new IllegalArgumentException("会员编号不能为空");
        }

        // 1. 如果传了短信验证码，执行短信安全校验
        MemberUserRespDTO memberUser = memberUserApi.getUser(memberId);
        if (StrUtil.isNotBlank(reqVO.getSmsCode())) {
            if (memberUser == null || StrUtil.isBlank(memberUser.getMobile())) {
                throw exception(USER_MOBILE_NOT_EXISTS);
            }
            smsCodeApi.useSmsCode(new SmsCodeUseReqDTO()
                    .setMobile(memberUser.getMobile())
                    .setScene(SmsSceneEnum.MEMBER_WITHDRAW.getScene())
                    .setCode(reqVO.getSmsCode().trim())
                    .setUsedIp(getClientIP() != null ? getClientIP() : "127.0.0.1"));
        }

        // 2. 实名强校验：如果会员档案已有实名认证姓名，必须与收款账户姓名一致
        if (memberUser != null && StrUtil.isNotBlank(memberUser.getName())) {
            if (!memberUser.getName().trim().equals(reqVO.getAccountName().trim())) {
                throw exception(WITHDRAW_NAME_NOT_MATCH_REAL);
            }
        }

        // 3. 校验重复绑定
        String account = reqVO.getAccount().trim();
        String type = reqVO.getType().trim();
        CpsWithdrawAccountDO duplicate = accountMapper.selectByMemberIdAndAccount(memberId, type, account);
        if (duplicate != null && (reqVO.getId() == null || !duplicate.getId().equals(reqVO.getId()))) {
            throw exception(WITHDRAW_ACCOUNT_EXISTS);
        }

        // 4. 处理默认账户逻辑
        Long existingCount = accountMapper.selectCount(CpsWithdrawAccountDO::getMemberId, memberId);
        boolean shouldBeDefault = Boolean.TRUE.equals(reqVO.getIsDefault()) || existingCount == 0;
        if (shouldBeDefault) {
            accountMapper.clearDefaultByMemberId(memberId);
        }

        // 5. 保存或更新
        if (reqVO.getId() != null) {
            CpsWithdrawAccountDO current = accountMapper.selectByMemberIdAndId(memberId, reqVO.getId());
            if (current == null) {
                throw exception(WITHDRAW_ACCOUNT_NOT_EXISTS);
            }
            current.setType(type);
            current.setAccount(account);
            current.setAccountName(reqVO.getAccountName().trim());
            current.setBankName(reqVO.getBankName());
            current.setIsDefault(shouldBeDefault);
            accountMapper.updateById(current);
            return current.getId();
        } else {
            CpsWithdrawAccountDO newAccount = CpsWithdrawAccountDO.builder()
                    .memberId(memberId)
                    .type(type)
                    .account(account)
                    .accountName(reqVO.getAccountName().trim())
                    .bankName(reqVO.getBankName())
                    .isDefault(shouldBeDefault)
                    .status(0)
                    .lastUsedTime(LocalDateTime.now())
                    .build();
            accountMapper.insert(newAccount);
            return newAccount.getId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultAccount(Long memberId, Long id) {
        CpsWithdrawAccountDO account = accountMapper.selectByMemberIdAndId(memberId, id);
        if (account == null) {
            throw exception(WITHDRAW_ACCOUNT_NOT_EXISTS);
        }
        accountMapper.clearDefaultByMemberId(memberId);
        account.setIsDefault(true);
        accountMapper.updateById(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long memberId, Long id) {
        CpsWithdrawAccountDO account = accountMapper.selectByMemberIdAndId(memberId, id);
        if (account == null) {
            throw exception(WITHDRAW_ACCOUNT_NOT_EXISTS);
        }
        accountMapper.deleteById(id);
    }

    @Override
    public void markAccountUsed(Long memberId, Long id) {
        if (memberId == null || id == null) {
            return;
        }
        CpsWithdrawAccountDO update = new CpsWithdrawAccountDO();
        update.setId(id);
        update.setLastUsedTime(LocalDateTime.now());
        accountMapper.updateById(update);
    }

    private AppCpsWithdrawAccountRespVO convertToRespVO(CpsWithdrawAccountDO bean) {
        if (bean == null) {
            return null;
        }
        return AppCpsWithdrawAccountRespVO.builder()
                .id(bean.getId())
                .type(bean.getType())
                .account(bean.getAccount())
                .accountMasked(maskAccount(bean.getType(), bean.getAccount()))
                .accountName(bean.getAccountName())
                .accountNameMasked(maskName(bean.getAccountName()))
                .bankName(bean.getBankName())
                .isDefault(Boolean.TRUE.equals(bean.getIsDefault()))
                .lastUsedTime(bean.getLastUsedTime())
                .build();
    }

    private String maskAccount(String type, String account) {
        if (StrUtil.isBlank(account)) {
            return "";
        }
        if (account.contains("@")) {
            return DesensitizedUtil.email(account);
        }
        if (account.length() == 11 && StrUtil.isNumeric(account)) {
            return DesensitizedUtil.mobilePhone(account);
        }
        if (account.length() > 6) {
            return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
        }
        return account;
    }

    private String maskName(String name) {
        if (StrUtil.isBlank(name)) {
            return "";
        }
        return DesensitizedUtil.chineseName(name);
    }

}
