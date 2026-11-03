package com.qiji.cps.module.cps.service.publish.channel;

import com.qiji.cps.module.member.api.user.MemberUserApi;
import com.qiji.cps.module.member.api.user.dto.MemberUserRespDTO;
import com.qiji.cps.module.system.api.sms.SmsSendApi;
import com.qiji.cps.module.system.api.sms.dto.send.SmsSendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 短信投放渠道。
 */
@Slf4j
@Component
public class CpsSmsPublishChannel implements CpsPublishChannel {

    @Resource
    private SmsSendApi smsSendApi;

    @Resource
    private MemberUserApi memberUserApi;

    @Override
    public String getChannelCode() {
        return com.qiji.cps.module.cps.service.publish.CpsPublishConstants.ChannelCode.SMS;
    }

    @Override
    public CpsPublishSendResult send(CpsPublishSendContext context) {
        String templateCode = context.task().getTemplateCode();
        if (!StringUtils.hasText(templateCode)) {
            return CpsPublishSendResult.failure("缺少短信模板编码");
        }
        MemberUserRespDTO member = memberUserApi.getUser(context.memberId());
        if (member == null || !StringUtils.hasText(member.getMobile())) {
            return CpsPublishSendResult.failure("会员未绑定手机号");
        }
        try {
            SmsSendSingleToUserReqDTO reqDTO = new SmsSendSingleToUserReqDTO();
            reqDTO.setUserId(context.memberId());
            reqDTO.setMobile(member.getMobile());
            reqDTO.setTemplateCode(templateCode);
            reqDTO.setTemplateParams(Map.of("content", context.content()));
            smsSendApi.sendSingleSmsToMember(reqDTO);
            return CpsPublishSendResult.ok();
        } catch (Exception ex) {
            log.warn("[send][短信投放失败 taskId={}, memberId={}]", context.task().getId(), context.memberId(), ex);
            return CpsPublishSendResult.failure("短信发送失败");
        }
    }

}
