package com.qiji.cps.module.member.service.auth;

import com.qiji.cps.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import com.qiji.cps.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import com.qiji.cps.framework.common.enums.CommonStatusEnum;
import com.qiji.cps.module.member.controller.app.auth.vo.*;
import com.qiji.cps.module.member.dal.dataobject.user.MemberUserDO;
import com.qiji.cps.module.member.service.user.MemberUserService;
import com.qiji.cps.module.system.api.logger.LoginLogApi;
import com.qiji.cps.module.system.api.sms.SmsCodeApi;
import com.qiji.cps.module.system.api.social.SocialClientApi;
import com.qiji.cps.module.system.api.social.SocialUserApi;
import com.qiji.cps.module.system.api.social.dto.SocialUserRespDTO;
import com.qiji.cps.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import com.qiji.cps.module.system.enums.logger.LoginResultEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.qiji.cps.framework.test.core.util.AssertUtils.assertServiceException;
import static com.qiji.cps.module.member.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberAuthLoginSafetyTest {
    @InjectMocks private MemberAuthServiceImpl service;
    @Mock private MemberUserService userService;
    @Mock private SmsCodeApi smsCodeApi;
    @Mock private LoginLogApi loginLogApi;
    @Mock private SocialUserApi socialUserApi;
    @Mock private SocialClientApi socialClientApi;
    @Mock private OAuth2TokenCommonApi oauth2TokenApi;

    private MemberUserDO user(boolean enabled) {
        MemberUserDO user = new MemberUserDO();
        user.setId(42L);
        user.setMobile("13800000000");
        user.setPassword("stored-hash");
        user.setStatus(enabled ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus());
        return user;
    }

    private AppAuthLoginReqVO passwordRequest() {
        AppAuthLoginReqVO request = new AppAuthLoginReqVO();
        request.setMobile("13800000000");
        request.setPassword("password");
        return request;
    }

    private void stubToken() {
        OAuth2AccessTokenRespDTO token = new OAuth2AccessTokenRespDTO();
        token.setUserId(42L);
        token.setAccessToken("test-access");
        when(oauth2TokenApi.createAccessToken(any())).thenReturn(token);
    }

    private void verifyDisabled() {
        verifyNoInteractions(oauth2TokenApi);
        verify(userService, never()).updateUserLogin(anyLong(), any());
        verify(loginLogApi).createLoginLog(argThat(log ->
                LoginResultEnum.USER_DISABLED.getResult().equals(log.getResult())
                        && Long.valueOf(42L).equals(log.getUserId())));
    }

    @Test
    void passwordLoginUsesAuthenticatedMember() {
        MemberUserDO user = user(true);
        when(userService.getUserByMobile(user.getMobile())).thenReturn(user);
        when(userService.isPasswordMatch("password", "stored-hash")).thenReturn(true);
        stubToken();
        assertEquals("test-access", service.login(passwordRequest()).getAccessToken());
        verify(oauth2TokenApi).createAccessToken(argThat(request -> Long.valueOf(42L).equals(request.getUserId())));
    }

    @Test
    void unknownMobileCannotReceiveToken() {
        assertServiceException(() -> service.login(passwordRequest()), AUTH_LOGIN_BAD_CREDENTIALS);
        verifyNoInteractions(oauth2TokenApi);
    }

    @Test
    void wrongPasswordCannotReceiveToken() {
        when(userService.getUserByMobile("13800000000")).thenReturn(user(true));
        assertServiceException(() -> service.login(passwordRequest()), AUTH_LOGIN_BAD_CREDENTIALS);
        verifyNoInteractions(oauth2TokenApi);
    }

    @Test
    void disabledPasswordLoginCannotReceiveToken() {
        when(userService.getUserByMobile("13800000000")).thenReturn(user(false));
        when(userService.isPasswordMatch("password", "stored-hash")).thenReturn(true);
        assertServiceException(() -> service.login(passwordRequest()), AUTH_LOGIN_USER_DISABLED);
        verifyDisabled();
    }

    @Test
    void disabledSmsLoginCannotReceiveToken() {
        AppAuthSmsLoginReqVO request = new AppAuthSmsLoginReqVO();
        request.setMobile("13800000000");
        request.setCode("123456");
        when(userService.createUserIfAbsent(eq("13800000000"), any(), any())).thenReturn(user(false));
        assertServiceException(() -> service.smsLogin(request), AUTH_LOGIN_USER_DISABLED);
        verifyDisabled();
        verifyNoInteractions(socialUserApi);
    }

    @Test
    void disabledSocialLoginCannotReceiveToken() {
        AppAuthSocialLoginReqVO request = new AppAuthSocialLoginReqVO(31, "code", "state");
        when(socialUserApi.getSocialUserByCode(anyInt(), eq(31), eq("code"), eq("state")))
                .thenReturn(new SocialUserRespDTO("openid", "nickname", "avatar", 42L));
        when(userService.getUser(42L)).thenReturn(user(false));
        assertServiceException(() -> service.socialLogin(request), AUTH_LOGIN_USER_DISABLED);
        verifyDisabled();
    }

    @Test
    void disabledWeixinLoginCannotBindOrReceiveToken() {
        SocialWxPhoneNumberInfoRespDTO phone = new SocialWxPhoneNumberInfoRespDTO();
        phone.setPurePhoneNumber("13800000000");
        when(socialClientApi.getWxMaPhoneNumberInfo(anyInt(), eq("phone-code"))).thenReturn(phone);
        when(userService.createUserIfAbsent(eq("13800000000"), any(), anyInt())).thenReturn(user(false));
        AppAuthWeixinMiniAppLoginReqVO request = new AppAuthWeixinMiniAppLoginReqVO("phone-code", "login-code", "state");
        assertServiceException(() -> service.weixinMiniAppLogin(request), AUTH_LOGIN_USER_DISABLED);
        verifyDisabled();
        verifyNoInteractions(socialUserApi);
    }

    @Test
    void enabledSocialLoginStillReceivesToken() {
        AppAuthSocialLoginReqVO request = new AppAuthSocialLoginReqVO(31, "code", "state");
        when(socialUserApi.getSocialUserByCode(anyInt(), eq(31), eq("code"), eq("state")))
                .thenReturn(new SocialUserRespDTO("openid", "nickname", "avatar", 42L));
        when(userService.getUser(42L)).thenReturn(user(true));
        stubToken();
        assertEquals("test-access", service.socialLogin(request).getAccessToken());
    }
}
