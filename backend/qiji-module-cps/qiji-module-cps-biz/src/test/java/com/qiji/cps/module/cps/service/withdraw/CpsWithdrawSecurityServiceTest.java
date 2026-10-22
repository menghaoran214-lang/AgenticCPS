package com.qiji.cps.module.cps.service.withdraw;

import com.qiji.cps.framework.common.exception.ServiceException;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawCreateReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawDO;
import com.qiji.cps.module.cps.dal.mysql.withdraw.CpsWithdrawMapper;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import com.qiji.cps.module.system.api.sms.SmsCodeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static com.qiji.cps.module.cps.enums.CpsErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpsWithdrawSecurityServiceTest {

    @InjectMocks
    private CpsWithdrawSecurityServiceImpl service;

    @Mock
    private CpsWithdrawMapper withdrawMapper;

    @Mock
    private MemberUserApi memberUserApi;

    @Mock
    private SmsCodeApi smsCodeApi;

    @Test
    void testMinAmountCentLimit() {
        Long memberId = 1001L;
        AppCpsWithdrawCreateReqVO req = new AppCpsWithdrawCreateReqVO();
        req.setAmountCent(50L); // 0.5元，低于最低1元
        req.setWithdrawType("alipay");
        req.setWithdrawAccount("test@alipay.com");
        req.setWithdrawAccountName("张三");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.checkAndEvaluate(memberId, req, null, "127.0.0.1"));
        assertEquals(WITHDRAW_AMOUNT_MIN.getCode(), ex.getCode());
    }

    @Test
    void testMaxSingleAmountCentLimit() {
        Long memberId = 1001L;
        AppCpsWithdrawCreateReqVO req = new AppCpsWithdrawCreateReqVO();
        req.setAmountCent(600_000L); // 6000元，高于单笔5000元
        req.setWithdrawType("alipay");
        req.setWithdrawAccount("test@alipay.com");
        req.setWithdrawAccountName("张三");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.checkAndEvaluate(memberId, req, null, "127.0.0.1"));
        assertEquals(WITHDRAW_AMOUNT_MAX.getCode(), ex.getCode());
    }

    @Test
    void testDailyCountLimit() {
        Long memberId = 1001L;
        AppCpsWithdrawCreateReqVO req = new AppCpsWithdrawCreateReqVO();
        req.setAmountCent(1000L);
        req.setWithdrawType("alipay");
        req.setWithdrawAccount("test@alipay.com");
        req.setWithdrawAccountName("张三");

        when(withdrawMapper.selectTodayActiveList(eq(memberId), any(), any()))
                .thenReturn(List.of(new CpsWithdrawDO(), new CpsWithdrawDO(), new CpsWithdrawDO()));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.checkAndEvaluate(memberId, req, null, "127.0.0.1"));
        assertEquals(WITHDRAW_DAILY_LIMIT.getCode(), ex.getCode());
    }

    @Test
    void testRequireSmsForLargeAmount() {
        Long memberId = 1001L;
        AppCpsWithdrawCreateReqVO req = new AppCpsWithdrawCreateReqVO();
        req.setAmountCent(50_000L); // 500元，需短信验证码
        req.setWithdrawType("alipay");
        req.setWithdrawAccount("test@alipay.com");
        req.setWithdrawAccountName("张三");
        req.setSmsCode(null);

        when(withdrawMapper.selectTodayActiveList(eq(memberId), any(), any()))
                .thenReturn(Collections.emptyList());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.checkAndEvaluate(memberId, req, null, "127.0.0.1"));
        assertEquals(WITHDRAW_SECURITY_SMS_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void testAutoApproveForSmallAmountWithDefaultAccount() {
        Long memberId = 1001L;
        AppCpsWithdrawCreateReqVO req = new AppCpsWithdrawCreateReqVO();
        req.setAmountCent(5000L); // 50元
        req.setAccountId(10L);

        CpsWithdrawAccountDO account = CpsWithdrawAccountDO.builder()
                .id(10L)
                .memberId(memberId)
                .type("alipay")
                .account("test@alipay.com")
                .accountName("张三")
                .isDefault(true)
                .build();

        when(withdrawMapper.selectTodayActiveList(eq(memberId), any(), any()))
                .thenReturn(Collections.emptyList());

        CpsWithdrawSecurityResult result = service.checkAndEvaluate(memberId, req, account, "127.0.0.1");
        assertNotNull(result);
        assertEquals(0, result.getRiskLevel()); // 0: 免审自动出款
        assertEquals("test@alipay.com", result.getFinalWithdrawAccount());
        assertEquals("张三", result.getFinalWithdrawAccountName());
    }

}
