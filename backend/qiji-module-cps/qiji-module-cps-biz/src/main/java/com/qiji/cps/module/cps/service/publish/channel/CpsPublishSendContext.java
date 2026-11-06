package com.qiji.cps.module.cps.service.publish.channel;

import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;

/**
 * 投放渠道上下文。
 *
 * @param task 投放任务
 * @param memberId 目标会员 ID
 * @param content 渲染后的消息内容
 * @param linkUrl 渲染后的推广链接，可为空
 */
public record CpsPublishSendContext(CpsPublishTaskDO task, Long memberId, String content, String linkUrl) {
}
