package com.qiji.cps.module.ai.controller.admin.model.vo.jev;

import com.qiji.cps.module.ai.framework.ai.core.model.jev.TypeSafeJevClient;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** 管理端 Jev 评估请求。 */
public record AiJevEvaluateReqVO(
        @NotNull Long modelId,
        @NotNull Object state,
        @NotEmpty Map<String, TypeSafeJevClient.JevQuestion> questions) {
}
