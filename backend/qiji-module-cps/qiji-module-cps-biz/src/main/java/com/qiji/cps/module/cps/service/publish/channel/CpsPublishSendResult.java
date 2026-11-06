package com.qiji.cps.module.cps.service.publish.channel;

public record CpsPublishSendResult(boolean success, String errorMsg) {

    public static CpsPublishSendResult ok() {
        return new CpsPublishSendResult(true, null);
    }

    public static CpsPublishSendResult failure(String errorMsg) {
        return new CpsPublishSendResult(false, errorMsg);
    }

}
