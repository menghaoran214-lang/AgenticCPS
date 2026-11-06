package com.qiji.cps.module.cps.service.publish.channel;

/**
 * CPS 投放渠道扩展点。新增触达渠道实现该接口并注册为 Spring Bean 即可被调度器识别。
 */
public interface CpsPublishChannel {

    /**
     * 渠道编码，与 cps_publish_task.channel_code 对应。
     *
     * @return 渠道编码
     */
    String getChannelCode();

    /**
     * 执行一次投放。
     *
     * @param context 投放上下文
     * @return 发送结果
     */
    CpsPublishSendResult send(CpsPublishSendContext context);

}
