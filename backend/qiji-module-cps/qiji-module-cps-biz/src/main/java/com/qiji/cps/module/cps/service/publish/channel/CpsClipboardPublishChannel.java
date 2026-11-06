package com.qiji.cps.module.cps.service.publish.channel;

import com.qiji.cps.module.cps.service.publish.CpsPublishConstants;
import org.springframework.stereotype.Component;

@Component
public class CpsClipboardPublishChannel implements CpsPublishChannel {

    @Override
    public String getChannelCode() {
        return CpsPublishConstants.ChannelCode.CLIPBOARD;
    }

    @Override
    public CpsPublishSendResult send(CpsPublishSendContext context) {
        return CpsPublishSendResult.ok();
    }

}
