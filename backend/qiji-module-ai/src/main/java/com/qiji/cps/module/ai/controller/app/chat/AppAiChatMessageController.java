package com.qiji.cps.module.ai.controller.app.chat;

import cn.hutool.core.collection.CollUtil;
import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageRespVO;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageSendReqVO;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageSendRespVO;
import com.qiji.cps.module.ai.controller.app.chat.vo.message.AppAiChatMessageRespVO;
import com.qiji.cps.module.ai.controller.app.chat.vo.message.AppAiChatMessageSendReqVO;
import com.qiji.cps.module.ai.controller.app.chat.vo.message.AppAiChatMessageSendRespVO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatMessageDO;
import com.qiji.cps.module.ai.enums.chat.AiChatOwnerTypeEnum;
import com.qiji.cps.module.ai.service.chat.AiChatConversationService;
import com.qiji.cps.module.ai.service.chat.AiChatMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

import static com.qiji.cps.framework.common.pojo.CommonResult.success;
import static com.qiji.cps.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "会员端 - AI 聊天消息")
@RestController
@RequestMapping("/ai/chat/message")
public class AppAiChatMessageController {

    @Resource
    private AiChatMessageService chatMessageService;
    @Resource
    private AiChatConversationService chatConversationService;

    @PostMapping("/send")
    @Operation(summary = "发送会员 AI 聊天消息")
    public CommonResult<AppAiChatMessageSendRespVO> sendMessage(
            @Valid @RequestBody AppAiChatMessageSendReqVO reqVO) {
        AiChatMessageSendRespVO result = chatMessageService.sendMessage(toAdminReq(reqVO),
                AiChatOwnerTypeEnum.MEMBER.name(), getLoginUserId());
        return success(toAppResponse(result));
    }

    @PostMapping(value = "/send-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式发送会员 AI 聊天消息")
    public Flux<CommonResult<AppAiChatMessageSendRespVO>> sendChatMessageStream(
            @Valid @RequestBody AppAiChatMessageSendReqVO reqVO) {
        return chatMessageService.sendChatMessageStream(toAdminReq(reqVO),
                AiChatOwnerTypeEnum.MEMBER.name(), getLoginUserId())
                .map(result -> {
            if (!result.isSuccess()) {
                return CommonResult.error(result);
            }
            return success(toAppResponse(result.getData()));
        });
    }

    @GetMapping("/list")
    @Operation(summary = "获取会员 AI 聊天消息")
    @Parameter(name = "conversationId", required = true)
    public CommonResult<List<AppAiChatMessageRespVO>> listMessages(@RequestParam("conversationId") Long conversationId) {
        // The owner-bound lookup is deliberately performed before loading message rows.
        chatConversationService.getOwnedConversation(conversationId, AiChatOwnerTypeEnum.MEMBER.name(), getLoginUserId());
        List<AiChatMessageDO> messages = chatMessageService.getChatMessageListByConversationId(conversationId);
        if (CollUtil.isEmpty(messages)) {
            return success(Collections.emptyList());
        }
        List<AppAiChatMessageRespVO> result = BeanUtils.toBean(messages, AppAiChatMessageRespVO.class);
        for (int i = 0; i < messages.size(); i++) {
            result.get(i).setUsage(toAppUsage(messages.get(i)));
        }
        return success(result);
    }

    private AiChatMessageSendReqVO toAdminReq(AppAiChatMessageSendReqVO reqVO) {
        // 调试轨迹包含完整 Prompt/模型输出，只允许管理端显式开启；会员端请求永远关闭。
        return BeanUtils.toBean(reqVO, AiChatMessageSendReqVO.class).setDebugMode(false);
    }

    private AppAiChatMessageSendRespVO toAppResponse(AiChatMessageSendRespVO response) {
        if (response == null) {
            return null;
        }
        return new AppAiChatMessageSendRespVO()
                .setEventType(response.getEventType())
                .setRequestId(response.getRequestId())
                .setStage(response.getStage())
                .setCompletedItems(response.getCompletedItems())
                .setTotalItems(response.getTotalItems())
                .setElapsedMs(response.getElapsedMs())
                .setDegraded(response.getDegraded())
                .setMessage(response.getMessage())
                .setTaskId(response.getTaskId())
                .setToolExecution(response.getToolExecution() == null ? null
                        : new AppAiChatMessageSendRespVO.ToolExecution()
                        .setExecutionId(response.getToolExecution().getExecutionId())
                        .setToolName(response.getToolExecution().getToolName())
                        .setIntent(response.getToolExecution().getIntent())
                        .setLabel(response.getToolExecution().getLabel())
                        .setStatus(response.getToolExecution().getStatus())
                        .setMessage(response.getToolExecution().getMessage())
                        .setSource(response.getToolExecution().getSource())
                        .setMcpClientName(response.getToolExecution().getMcpClientName())
                        .setSkillCode(response.getToolExecution().getSkillCode())
                        .setSkillVersion(response.getToolExecution().getSkillVersion()))
                .setUsage(toAppUsage(response.getUsage()))
                .setSend(toAppMessage(response.getSend()))
                .setReceive(toAppMessage(response.getReceive()));
    }

    private AppAiChatMessageSendRespVO.Message toAppMessage(AiChatMessageSendRespVO.Message message) {
        return message == null ? null : BeanUtils.toBean(message, AppAiChatMessageSendRespVO.Message.class);
    }

    private AppAiChatMessageSendRespVO.ModelUsage toAppUsage(AiChatMessageSendRespVO.ModelUsage usage) {
        return usage == null ? null : BeanUtils.toBean(usage, AppAiChatMessageSendRespVO.ModelUsage.class);
    }

    private AppAiChatMessageRespVO.ModelUsage toAppUsage(AiChatMessageDO message) {
        if (message.getPromptTokens() == null && message.getCompletionTokens() == null
                && message.getTotalTokens() == null && message.getCacheReadInputTokens() == null
                && message.getCacheWriteInputTokens() == null) {
            return null;
        }
        return new AppAiChatMessageRespVO.ModelUsage()
                .setPromptTokens(message.getPromptTokens())
                .setCompletionTokens(message.getCompletionTokens())
                .setTotalTokens(message.getTotalTokens())
                .setCacheReadInputTokens(message.getCacheReadInputTokens())
                .setCacheWriteInputTokens(message.getCacheWriteInputTokens());
    }
}
