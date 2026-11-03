package com.qiji.cps.module.cps.service.publish.channel;

import com.qiji.cps.module.system.api.notify.NotifyMessageSendApi;
import com.qiji.cps.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 站内信投放渠道。
 */
@Slf4j
@Component
public class CpsNotifyPublishChannel implements CpsPublishChannel {

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @Override
    public String getChannelCode() {
        return com.qiji.cps.module.cps.service.publish.CpsPublishConstants.ChannelCode.NOTIFY;
    }

    @Override
    public CpsPublishSendResult send(CpsPublishSendContext context) {
        String templateCode = context.task().getTemplateCode();
        if (!StringUtils.hasText(templateCode)) {
            return CpsPublishSendResult.failure("缺少站内信模板编码");
        }
        try {
            NotifySendSingleToUserReqDTO reqDTO = new NotifySendSingleToUserReqDTO();
            reqDTO.setUserId(context.memberId());
            reqDTO.setTemplateCode(templateCode);
            reqDTO.setTemplateParams(Map.of("content", context.content()));
            notifyMessageSendApi.sendSingleMessageToMember(reqDTO);
            return CpsPublishSendResult.ok();
        } catch (Exception ex) {
            log.warn("[send][站内信投放失败 taskId={}, memberId={}]", context.task().getId(), context.memberId(), ex);
            return CpsPublishSendResult.failure("站内信发送失败");
        }
    }

}
