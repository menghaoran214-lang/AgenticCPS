package com.qiji.cps.module.cps.service.withdraw;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.qiji.cps.framework.common.exception.ServiceException;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountRespVO;
import com.qiji.cps.module.cps.controller.app.withdraw.vo.AppCpsWithdrawAccountSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.withdraw.CpsWithdrawAccountDO;
import com.qiji.cps.module.cps.dal.mysql.withdraw.CpsWithdrawAccountMapper;
import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import com.qiji.cps.module.system.api.sms.SmsCodeApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.qiji.cps.module.cps.enums.CpsErrorCodeConstants.WITHDRAW_ACCOUNT_EXISTS;
import static com.qiji.cps.module.cps.enums.CpsErrorCodeConstants.WITHDRAW_NAME_NOT_MATCH_REAL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CpsWithdrawAccountServiceTest {

    @InjectMocks
    private CpsWithdrawAccountServiceImpl service;

    @Mock
    private CpsWithdrawAccountMapper accountMapper;

    @Mock
    private MemberUserApi memberUserApi;

    @Mock
    private SmsCodeApi smsCodeApi;

    @Test
    void testGetMemberAccountList_masked() {
        Long memberId = 1001L;
        CpsWithdrawAccountDO accountDO = CpsWithdrawAccountDO.builder()
                .id(1L)
                .memberId(memberId)
                .type("alipay")
                .account("13812345678")
                .accountName("张三")
                .isDefault(true)
                .build();
        when(accountMapper.selectListByMemberId(memberId)).thenReturn(List.of(accountDO));

        List<AppCpsWithdrawAccountRespVO> list = service.getMemberAccountList(memberId);
        assertEquals(1, list.size());
        assertEquals("138****5678", list.get(0).getAccountMasked());
        assertEquals("张*", list.get(0).getAccountNameMasked());
        assertTrue(list.get(0).getIsDefault());
    }

    @Test
    void testSaveAccount_success() {
        Long memberId = 1001L;
        AppCpsWithdrawAccountSaveReqVO req = new AppCpsWithdrawAccountSaveReqVO();
        req.setType("alipay");
        req.setAccount("13812345678");
        req.setAccountName("张三");
        req.setIsDefault(true);

        when(memberUserApi.getUser(memberId)).thenReturn(new MemberUserRespDTO().setId(memberId).setName("张三").setMobile("13812345678"));
        when(accountMapper.selectByMemberIdAndAccount(memberId, "alipay", "13812345678")).thenReturn(null);
        when(accountMapper.selectCount(any(SFunction.class), eq(memberId))).thenReturn(0L);
        doAnswer(invocation -> {
            CpsWithdrawAccountDO entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        }).when(accountMapper).insert(any(CpsWithdrawAccountDO.class));

        Long id = service.saveAccount(memberId, req);
        assertEquals(99L, id);
        verify(accountMapper).clearDefaultByMemberId(memberId);
        verify(accountMapper).insert(any(CpsWithdrawAccountDO.class));
    }

    @Test
    void testSaveAccount_duplicateFails() {
        Long memberId = 1001L;
        AppCpsWithdrawAccountSaveReqVO req = new AppCpsWithdrawAccountSaveReqVO();
        req.setType("alipay");
        req.setAccount("13812345678");
        req.setAccountName("张三");

        when(accountMapper.selectByMemberIdAndAccount(memberId, "alipay", "13812345678"))
                .thenReturn(CpsWithdrawAccountDO.builder().id(50L).build());

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveAccount(memberId, req));
        assertEquals(WITHDRAW_ACCOUNT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void testSaveAccount_realNameMismatchFails() {
        Long memberId = 1001L;
        AppCpsWithdrawAccountSaveReqVO req = new AppCpsWithdrawAccountSaveReqVO();
        req.setType("alipay");
        req.setAccount("13812345678");
        req.setAccountName("李四");

        when(memberUserApi.getUser(memberId)).thenReturn(new MemberUserRespDTO().setId(memberId).setName("张三").setMobile("13812345678"));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveAccount(memberId, req));
        assertEquals(WITHDRAW_NAME_NOT_MATCH_REAL.getCode(), ex.getCode());
    }

    @Test
    void testSetDefaultAccount() {
        Long memberId = 1001L;
        CpsWithdrawAccountDO current = CpsWithdrawAccountDO.builder().id(20L).memberId(memberId).isDefault(false).build();
        when(accountMapper.selectByMemberIdAndId(memberId, 20L)).thenReturn(current);

        service.setDefaultAccount(memberId, 20L);
        verify(accountMapper).clearDefaultByMemberId(memberId);
        verify(accountMapper).updateById(argThat((CpsWithdrawAccountDO entity) -> Boolean.TRUE.equals(entity.getIsDefault())));
    }

}
