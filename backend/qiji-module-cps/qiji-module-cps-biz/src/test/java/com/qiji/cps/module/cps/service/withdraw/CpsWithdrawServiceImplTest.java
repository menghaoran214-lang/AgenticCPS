package com.qiji.cps.module.cps.service.withdraw;

import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawCreateReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawDO;
import com.qiji.cps.module.cps.dal.mysql.withdraw.CpsWithdrawMapper;
import com.qiji.cps.module.cps.enums.CpsWithdrawStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;
import org.mockito.MockedStatic;
import com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils;

@ExtendWith(MockitoExtension.class)
class CpsWithdrawServiceImplTest {

    @InjectMocks
    private CpsWithdrawServiceImpl service;
    @Mock private CpsWithdrawMapper withdrawMapper;
    @Mock private CpsWithdrawStepExecutor stepExecutor;
    @Mock private CpsWithdrawTransferExecutor transferExecutor;
    @Mock private CpsWithdrawAccountService withdrawAccountService;
    @Mock private CpsWithdrawSecurityService withdrawSecurityService;

    @Test
    void repeatedApplicationReturnsOriginalOrderOnlyForSameMemberAndAmount() {
        AppCpsWithdrawCreateReqVO request = request(1200L, "same-key");
        CpsWithdrawDO existing = CpsWithdrawDO.builder().id(8L).memberId(1001L).amountCent(1200L)
                .withdrawType("alipay").withdrawAccount("member@example.com").withdrawAccountName("Member")
                .idempotencyKey("same-key").build();
        when(withdrawMapper.selectByIdempotencyKey("same-key")).thenReturn(existing);

        assertEquals(8L, service.createWithdraw(1001L, request));
        verify(stepExecutor, never()).createAndFreeze(any());

        assertThrows(IllegalStateException.class, () -> service.createWithdraw(2002L, request));
        assertThrows(IllegalStateException.class,
                () -> service.createWithdraw(1001L, request(1300L, "same-key")));

        AppCpsWithdrawCreateReqVO changedAccount = request(1200L, "same-key");
        changedAccount.setWithdrawAccount("other@example.com");
        assertThrows(IllegalStateException.class, () -> service.createWithdraw(1001L, changedAccount));
    }

    @Test
    void approveClaimsReviewStateBeforeStartingRemoteTransfer() {
        CpsWithdrawDO reviewing = CpsWithdrawDO.builder().id(8L)
                .status(CpsWithdrawStatusEnum.REVIEWING.getStatus()).build();
        when(stepExecutor.markReviewing(8L, "ok", 77L)).thenReturn(reviewing);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(77L);
            service.approveWithdraw(8L, "ok");
            verify(stepExecutor).markReviewing(8L, "ok", 77L);
            verify(transferExecutor).startTransfer(8L);
        }
    }

    @Test
    void rejectDelegatesToStepExecutor() {
        service.rejectWithdraw(9L, "bad");
        verify(stepExecutor).rejectAndUnfreeze(9L, "bad", "withdraw-reject:9");
    }

    private AppCpsWithdrawCreateReqVO request(Long amountCent, String idempotencyKey) {
        AppCpsWithdrawCreateReqVO req = new AppCpsWithdrawCreateReqVO();
        req.setAmountCent(amountCent);
        req.setWithdrawType("alipay");
        req.setWithdrawAccount("member@example.com");
        req.setWithdrawAccountName("Member");
        req.setIdempotencyKey(idempotencyKey);
        return req;
    }

}
