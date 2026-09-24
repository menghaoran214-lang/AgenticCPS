package com.qiji.cps.module.ai.controller.admin.model.vo.jev;

import com.qiji.cps.module.ai.framework.ai.core.model.jev.TypeSafeJevClient;

/** 管理端 Jev 评估响应。 */
public record AiJevEvaluateRespVO(TypeSafeJevClient.JevResponse result) {
}
