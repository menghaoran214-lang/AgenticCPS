package com.qiji.cps.module.ai.service.chat;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.qiji.cps.framework.common.pojo.CommonResult;
import com.qiji.cps.framework.common.pojo.PageResult;
import com.qiji.cps.framework.common.util.object.BeanUtils;
import com.qiji.cps.framework.tenant.core.util.TenantUtils;
import com.qiji.cps.framework.tenant.core.context.TenantContextHolder;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessagePageReqVO;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageRespVO;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageSendReqVO;
import com.qiji.cps.module.ai.controller.admin.chat.vo.message.AiChatMessageSendRespVO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatMessageDO;
import com.qiji.cps.module.ai.dal.dataobject.knowledge.AiKnowledgeDocumentDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiChatRoleDO;
import com.qiji.cps.module.ai.dal.dataobject.model.AiModelDO;
import com.qiji.cps.module.ai.dal.mysql.chat.AiChatMessageMapper;
import com.qiji.cps.module.ai.enums.ErrorCodeConstants;
import com.qiji.cps.module.ai.enums.model.AiPlatformEnum;
import com.qiji.cps.module.ai.framework.ai.core.webserch.AiWebSearchClient;
import com.qiji.cps.module.ai.framework.ai.core.webserch.AiWebSearchRequest;
import com.qiji.cps.module.ai.framework.ai.core.webserch.AiWebSearchResponse;
import com.qiji.cps.module.ai.service.knowledge.AiKnowledgeDocumentService;
import com.qiji.cps.module.ai.service.knowledge.AiKnowledgeSegmentService;
import com.qiji.cps.module.ai.service.knowledge.bo.AiKnowledgeSegmentSearchReqBO;
import com.qiji.cps.module.ai.service.knowledge.bo.AiKnowledgeSegmentSearchRespBO;
import com.qiji.cps.module.ai.service.model.AiChatRoleService;
import com.qiji.cps.module.ai.service.model.AiModelService;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolAction;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolActionService;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolExecutionEvent;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolExecutionListener;
import com.qiji.cps.module.ai.service.chat.tool.AiChatToolRiskLevel;
import com.qiji.cps.module.ai.util.AiUtils;
import com.qiji.cps.module.ai.util.FileTypeUtils;
import com.google.common.collect.Maps;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.qiji.cps.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.qiji.cps.framework.common.pojo.CommonResult.error;
import static com.qiji.cps.framework.common.pojo.CommonResult.success;
import static com.qiji.cps.framework.common.util.collection.CollectionUtils.convertList;
import static com.qiji.cps.framework.common.util.collection.CollectionUtils.convertSet;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_MESSAGE_NOT_EXIST;
import static com.qiji.cps.module.ai.enums.ErrorCodeConstants.CHAT_TOOL_INTENT_REQUEST_ID_REQUIRED;

/**
 * AI 聊天消息 Service 实现类
 *
 * @author fansili
 */
@Service
@Slf4j
public class AiChatMessageServiceImpl implements AiChatMessageService {

    /**
     * 联网搜索的结束数
     */
    private static final Integer WEB_SEARCH_COUNT = 10;
    /** Keep non-essential enrichment within the interactive-response budget. */
    private static final long PREPROCESS_TIMEOUT_SECONDS = 8;

    // TODO @芋艿：后续优化下对话的 Prompt 整体结构

    /**
     * 知识库转 {@link UserMessage} 的内容模版
     */
    private static final String KNOWLEDGE_USER_MESSAGE_TEMPLATE = "使用 <Reference></Reference> 标记中的内容作为本次对话的参考:\n\n" +
            "%s\n\n" + // 多个 <Reference></Reference> 的拼接
            "回答要求：\n- 避免提及你是从 <Reference></Reference> 获取的知识。";

    private static final String WEB_SEARCH_USER_MESSAGE_TEMPLATE = "使用 <WebSearch></WebSearch> 标记中的内容作为本次对话的参考:\n\n" +
            "%s\n\n" + // 多个 <WebSearch></WebSearch> 的拼接
            "回答要求：\n- 避免提及你是从 <WebSearch></WebSearch> 获取的知识。";

    /**
     * 附件转 ${@link UserMessage} 的内容模版
     */
    @SuppressWarnings("TextBlockMigration")
    private static final String Attachment_USER_MESSAGE_TEMPLATE = "使用 <Attachment></Attachment> 标记用户对话上传的附件内容:\n\n" +
            "%s\n\n" + // 多个 <Attachment></Attachment> 的拼接
            "回答要求：\n- 避免提及 <Attachment></Attachment> 附件的编码格式。";

    @Resource
    private AiChatMessageMapper chatMessageMapper;

    @Resource
    private AiChatConversationService chatConversationService;
    @Resource
    private AiChatRoleService chatRoleService;
    @Resource
    private AiModelService modalService;
    @Resource
    private AiKnowledgeSegmentService knowledgeSegmentService;
    @Resource
    private AiKnowledgeDocumentService knowledgeDocumentService;
    @Resource
    private AiChatIdentityContextService identityContextService;
    @Resource
    private AiChatToolCallbackService toolCallbackService;
    @Resource
    private AiChatToolActionService toolActionService;
    @Resource
    private List<AiChatMessageBlockResolver> blockResolvers;
    @Autowired(required = false)
    private AiWebSearchClient webSearchClient;

    @Transactional(rollbackFor = Exception.class)
    public AiChatMessageSendRespVO sendMessage(AiChatMessageSendReqVO sendReqVO, String ownerUserType, Long userId) {
        // 1.1 校验对话存在
        AiChatConversationDO conversation = chatConversationService
                .getOwnedConversation(sendReqVO.getConversationId(), ownerUserType, userId);
        normalizeAttachmentOnlyMessage(sendReqVO);
        AiChatToolAction requestedAction = toolActionService.requireAllowedAction(conversation, sendReqVO.getToolIntent());
        validateIntentRequestId(requestedAction, sendReqVO);
        List<AiChatMessageDO> historyMessages = getRecentContextMessages(conversation);
        // 1.2 校验模型
        AiModelDO model = modalService.validateModel(conversation.getModelId());
        ChatModel chatModel = modalService.getChatModel(model.getId());

        // 2.1 知识库召回
        List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments = recallKnowledgeSegment(
                sendReqVO.getContent(), conversation);

        // 2.2 联网搜索
        AiWebSearchResponse webSearchResponse = Boolean.TRUE.equals(sendReqVO.getUseSearch()) && webSearchClient != null ?
                webSearchClient.search(new AiWebSearchRequest().setQuery(sendReqVO.getContent())
                        .setSummary(true).setCount(WEB_SEARCH_COUNT)) : null;

        // 3. 插入 user 发送消息
        AiChatMessageDO userMessage = createChatMessage(conversation.getId(), null, model,
                userId, conversation.getRoleId(), MessageType.USER, sendReqVO.getContent(), sendReqVO.getUseContext(),
                null, sendReqVO.getAttachmentUrls(), null);

        // 4.1 插入 assistant 接收消息
        AiChatMessageDO assistantMessage = createChatMessage(conversation.getId(), userMessage.getId(), model,
                userId, conversation.getRoleId(), MessageType.ASSISTANT, "", sendReqVO.getUseContext(),
                knowledgeSegments, null, webSearchResponse);

        AtomicReference<List<Map<String, Object>>> blocks = new AtomicReference<>(Collections.emptyList());
        AiChatToolExecutionListener blockListener = event -> collectBlocks(blocks, event);
        // The synchronous path does not expose tool events, but still resolves blocks.
        // 4.2 创建 chat 需要的 Prompt
        Prompt prompt = buildPrompt(conversation, historyMessages, knowledgeSegments, webSearchResponse, model, sendReqVO,
                requestedAction, blockListener);
        ChatResponse chatResponse = chatModel.call(prompt);

        // 4.3 更新响应内容
        String newContent = AiUtils.getChatResponseContent(chatResponse);
        String newReasoningContent = AiUtils.getChatResponseReasoningContent(chatResponse);
        chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId())
                .setContent(newContent).setReasoningContent(newReasoningContent).setBlocks(blocks.get()));
        // 4.4 响应结果
        Map<Long, AiKnowledgeDocumentDO> documentMap = knowledgeDocumentService.getKnowledgeDocumentMap(
                convertSet(knowledgeSegments, AiKnowledgeSegmentSearchRespBO::getDocumentId));
        List<AiChatMessageRespVO.KnowledgeSegment> segments = BeanUtils.toBean(knowledgeSegments,
                AiChatMessageRespVO.KnowledgeSegment.class, segment -> {
                    AiKnowledgeDocumentDO document = documentMap.get(segment.getDocumentId());
                    segment.setDocumentName(document != null ? document.getName() : null);
                });
        return new AiChatMessageSendRespVO()
                .setEventType("MESSAGE_COMPLETE")
                .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                        .setContent(newContent).setSegments(segments)
                        .setWebSearchPages(webSearchResponse != null ? webSearchResponse.getLists() : null)
                        .setBlocks(blocks.get()));
    }

    @Override
    public Flux<CommonResult<AiChatMessageSendRespVO>> sendChatMessageStream(AiChatMessageSendReqVO sendReqVO,
            String ownerUserType, Long userId) {
        // Emit an acknowledgement before any database, retrieval, or provider work starts. This
        // prevents a slow dependency from leaving SSE clients with a blank screen.
        String requestId = UUID.randomUUID().toString();
        AiChatMessageSendRespVO accepted = new AiChatMessageSendRespVO()
                .setEventType("TASK_ACCEPTED").setRequestId(requestId).setStage("ACCEPTED")
                .setElapsedMs(0L).setMessage("已接收请求，正在分析需求");
        return Flux.concat(Flux.just(success(accepted)),
                Flux.just(success(progressEvent(requestId, "PREPARING", 0, 2, 0L,
                        "正在准备对话上下文"))),
                Flux.defer(() -> doSendChatMessageStream(sendReqVO, ownerUserType, userId, requestId)));
    }

    private Flux<CommonResult<AiChatMessageSendRespVO>> doSendChatMessageStream(AiChatMessageSendReqVO sendReqVO,
            String ownerUserType, Long userId, String requestId) {
        long startedAt = System.nanoTime();
        // 1.1 校验对话存在
        AiChatConversationDO conversation = chatConversationService
                .getOwnedConversation(sendReqVO.getConversationId(), ownerUserType, userId);
        normalizeAttachmentOnlyMessage(sendReqVO);
        AiChatToolAction requestedAction = toolActionService.requireAllowedAction(conversation, sendReqVO.getToolIntent());
        validateIntentRequestId(requestedAction, sendReqVO);
        List<AiChatMessageDO> historyMessages = getRecentContextMessages(conversation);
        // 1.2 校验模型
        AiModelDO model = modalService.validateModel(conversation.getModelId());
        StreamingChatModel chatModel = modalService.getChatModel(model.getId());
        Long tenantId = TenantContextHolder.getRequiredTenantId();

        // Retrieval and web search are independent. Run them concurrently and degrade only the
        // timed-out enrichment path instead of delaying the model indefinitely.
        CompletableFuture<List<AiKnowledgeSegmentSearchRespBO>> knowledgeFuture = CompletableFuture.supplyAsync(
                () -> TenantUtils.execute(tenantId,
                        () -> recallKnowledgeSegment(sendReqVO.getContent(), conversation)));
        CompletableFuture<AiWebSearchResponse> webSearchFuture = Boolean.TRUE.equals(sendReqVO.getUseSearch())
                && webSearchClient != null ? CompletableFuture.supplyAsync(() -> TenantUtils.execute(tenantId,
                () -> webSearchClient.search(new AiWebSearchRequest().setQuery(sendReqVO.getContent())
                        .setSummary(true).setCount(WEB_SEARCH_COUNT))))
                : CompletableFuture.completedFuture(null);
        AtomicBoolean degraded = new AtomicBoolean(false);
        List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments = awaitEnrichment(knowledgeFuture, Collections.emptyList(),
                "knowledge", requestId, degraded);
        AiWebSearchResponse webSearchResponse = awaitEnrichment(webSearchFuture, null, "web-search", requestId, degraded);

        // 3. 插入 user 发送消息
        AiChatMessageDO userMessage = createChatMessage(conversation.getId(), null, model,
                userId, conversation.getRoleId(), MessageType.USER, sendReqVO.getContent(), sendReqVO.getUseContext(),
                null, sendReqVO.getAttachmentUrls(), null);

        // 4.1 插入 assistant 接收消息
        AiChatMessageDO assistantMessage = createChatMessage(conversation.getId(), userMessage.getId(), model,
                userId, conversation.getRoleId(), MessageType.ASSISTANT, "", sendReqVO.getUseContext(),
                knowledgeSegments, null, webSearchResponse);

        // 4.2 构建 Prompt，并进行调用
        Sinks.Many<AiChatToolExecutionEvent> toolEventSink = Sinks.many().unicast().onBackpressureBuffer();
        Object toolEventLock = new Object();
        AtomicReference<List<Map<String, Object>>> blocks = new AtomicReference<>(Collections.emptyList());
        Sinks.One<CommonResult<AiChatMessageSendRespVO>> completeSink = Sinks.one();
        AiChatToolExecutionListener toolExecutionListener = event -> {
            collectBlocks(blocks, event);
            synchronized (toolEventLock) {
                toolEventSink.tryEmitNext(event);
            }
        };
        Prompt prompt = buildPrompt(conversation, historyMessages, knowledgeSegments, webSearchResponse, model, sendReqVO,
                requestedAction, toolExecutionListener);
        Flux<ChatResponse> streamResponse = chatModel.stream(prompt);

        // 4.3 流式返回
        StringBuffer contentBuffer = new StringBuffer();
        StringBuffer reasoningContentBuffer = new StringBuffer();
        AtomicBoolean bodyStarted = new AtomicBoolean(false);

        // 防止执行多次知识库和联网搜索
        AtomicBoolean firstExecuteFlag = new AtomicBoolean(true);
        AtomicReference<List<AiChatMessageRespVO.KnowledgeSegment>> cacheSegments = new AtomicReference<>();
        AtomicReference<List<AiWebSearchResponse.WebPage>> cacheWebSearchPages = new AtomicReference<>();
        Flux<CommonResult<AiChatMessageSendRespVO>> messageFlux = streamResponse.map(chunk -> {
            // 仅首次：返回知识库、联网搜索
            if (StrUtil.isEmpty(contentBuffer)) {
                if (firstExecuteFlag.compareAndSet(true, false)) { // CAS 操作，确保仅执行一次
                    Map<Long, AiKnowledgeDocumentDO> documentMap = TenantUtils.executeIgnore(() -> knowledgeDocumentService.getKnowledgeDocumentMap(
                            convertSet(knowledgeSegments, AiKnowledgeSegmentSearchRespBO::getDocumentId)));
                    cacheSegments.set(BeanUtils.toBean(knowledgeSegments, AiChatMessageRespVO.KnowledgeSegment.class, segment -> {
                        AiKnowledgeDocumentDO document = documentMap.get(segment.getDocumentId());
                        segment.setDocumentName(document != null ? document.getName() : null);
                    }));
                    if (webSearchResponse != null) {
                        cacheWebSearchPages.set(webSearchResponse.getLists());
                    }
                }
            }
            // 响应结果
            String newContent = AiUtils.getChatResponseContent(chunk);
            String newReasoningContent = AiUtils.getChatResponseReasoningContent(chunk);
            if (StrUtil.isNotEmpty(newContent)) {
                contentBuffer.append(newContent);
                bodyStarted.set(true);
            }
            if (StrUtil.isNotEmpty(newReasoningContent)) {
                reasoningContentBuffer.append(newReasoningContent);
            }
            return success(new AiChatMessageSendRespVO()
                    .setEventType("MESSAGE_DELTA")
                    .setRequestId(requestId).setStage("GENERATING")
                    .setElapsedMs(elapsedMs(startedAt))
                    .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
            .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                            .setContent(StrUtil.nullToDefault(newContent, "")) // 避免 null 的 情况
                            .setReasoningContent(StrUtil.nullToDefault(newReasoningContent, "")) // 避免 null 的 情况
                            .setSegments(cacheSegments.get()).setWebSearchPages(cacheWebSearchPages.get())
                            .setBlocks(blocks.get()))); // 知识库 + 联网搜索
        }).doOnComplete(() -> {
            // 忽略租户，因为 Flux 异步无法透传租户
            TenantUtils.executeIgnore(() -> chatMessageMapper.updateById(
                    new AiChatMessageDO().setId(assistantMessage.getId()).setContent(contentBuffer.toString())
                            .setReasoningContent(reasoningContentBuffer.toString()).setBlocks(blocks.get())));
            completeSink.tryEmitValue(success(new AiChatMessageSendRespVO().setEventType("MESSAGE_COMPLETE")
                    .setRequestId(requestId).setStage("COMPLETE").setElapsedMs(elapsedMs(startedAt))
                    .setSend(BeanUtils.toBean(userMessage, AiChatMessageSendRespVO.Message.class))
                    .setReceive(BeanUtils.toBean(assistantMessage, AiChatMessageSendRespVO.Message.class)
                            .setContent(contentBuffer.toString())
                            .setReasoningContent(reasoningContentBuffer.toString())
                            .setSegments(cacheSegments.get()).setWebSearchPages(cacheWebSearchPages.get())
                            .setBlocks(blocks.get()))));
        }).doOnError(throwable -> {
            log.error("[sendChatMessageStream][userId({}) sendReqVO({}) 发生异常]", userId, sendReqVO, throwable);
            // 忽略租户，因为 Flux 异步无法透传租户
            TenantUtils.executeIgnore(() -> {
                // 如果有内容，则更新内容
                if (StrUtil.isNotEmpty(contentBuffer)) {
                    chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId())
                            .setContent(contentBuffer.toString()).setReasoningContent(reasoningContentBuffer.toString()));
                } else {
                    // 否则，则进行删除
                    chatMessageMapper.deleteById(assistantMessage.getId());
                }
            });
            completeSink.tryEmitEmpty();
        }).doOnCancel(() -> {
            log.info("[sendChatMessageStream][userId({}) sendReqVO({}) 取消请求]", userId, sendReqVO);
            // 忽略租户，因为 Flux 异步无法透传租户
            TenantUtils.executeIgnore(() -> {
                // 如果有内容，则更新内容
                if (StrUtil.isNotEmpty(contentBuffer)) {
                    chatMessageMapper.updateById(new AiChatMessageDO().setId(assistantMessage.getId())
                            .setContent(contentBuffer.toString()).setReasoningContent(reasoningContentBuffer.toString()));
                } else {
                    // 否则，则进行删除
                    chatMessageMapper.deleteById(assistantMessage.getId());
                }
            });
            completeSink.tryEmitEmpty();
        }).onErrorResume(error -> Flux.just(error(ErrorCodeConstants.CHAT_STREAM_ERROR)))
                .doFinally(signalType -> {
                    synchronized (toolEventLock) {
                        toolEventSink.tryEmitComplete();
                    }
                });
        Flux<CommonResult<AiChatMessageSendRespVO>> toolEventFlux = toolEventSink.asFlux()
                .map(event -> success(toToolExecutionResponse(event).setRequestId(requestId)
                        .setStage("TOOL_EXECUTION").setElapsedMs(elapsedMs(startedAt))));
        Flux<CommonResult<AiChatMessageSendRespVO>> completeEventFlux = completeSink.asMono().flux()
                .flatMap(result -> Flux.just(result, success(progressEvent(requestId, "COMPLETE", 2, 2,
                        elapsedMs(startedAt), "回答已完成").setEventType("TASK_COMPLETE"))));
        Flux<CommonResult<AiChatMessageSendRespVO>> heartbeatFlux = Flux.interval(Duration.ofSeconds(4))
                .takeWhile(ignored -> !bodyStarted.get())
                .map(ignored -> success(progressEvent(requestId, "WAITING_FOR_RESPONSE", 2, 2,
                        elapsedMs(startedAt), "正在整理检索结果，请稍候")))
                .takeUntilOther(completeSink.asMono());
        // Subscribe to the event sink before the single model stream subscription starts emitting tool callbacks.
        return Flux.concat(Flux.just(success(progressEvent(requestId, "GENERATING", 2, 2,
                        elapsedMs(startedAt), "资料已准备，正在生成回答"))),
                degraded.get() ? Flux.just(success(progressEvent(requestId, "DEGRADED", 2, 2,
                        elapsedMs(startedAt), "部分资料暂不可用，已使用可用信息继续回答")
                        .setEventType("TASK_DEGRADED").setDegraded(true))) : Flux.empty(),
                Flux.merge(toolEventFlux, messageFlux, completeEventFlux, heartbeatFlux));
    }

    private List<AiKnowledgeSegmentSearchRespBO> recallKnowledgeSegment(String content,
            AiChatConversationDO conversation) {
        // 1. 查询聊天角色
        if (conversation == null || conversation.getRoleId() == null) {
            return Collections.emptyList();
        }
        AiChatRoleDO role = chatRoleService.getChatRole(conversation.getRoleId());
        if (role == null || CollUtil.isEmpty(role.getKnowledgeIds())) {
            return Collections.emptyList();
        }

        // Independent vector stores can be queried concurrently. Keep a bounded merged result so
        // attaching an additional knowledge base does not linearly inflate prompt size.
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<CompletableFuture<List<AiKnowledgeSegmentSearchRespBO>>> futures = role.getKnowledgeIds().stream()
                .map(knowledgeId -> CompletableFuture.supplyAsync(() -> TenantUtils.execute(tenantId,
                        () -> knowledgeSegmentService.searchKnowledgeSegment(new AiKnowledgeSegmentSearchReqBO()
                                .setKnowledgeId(knowledgeId).setContent(content)))
                ).exceptionally(ex -> {
                    log.warn("[recallKnowledgeSegment] knowledge [{}] retrieval failed", knowledgeId, ex);
                    return Collections.emptyList();
                })).toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return futures.stream().flatMap(future -> future.join().stream())
                .sorted(Comparator.comparing(AiKnowledgeSegmentSearchRespBO::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10).toList();
    }

    private List<AiChatMessageDO> getRecentContextMessages(AiChatConversationDO conversation) {
        int maxContexts = conversation.getMaxContexts() == null ? 0 : conversation.getMaxContexts();
        // Two rows form one user/assistant exchange. Keep one extra exchange so the existing
        // replyId-based filtering can discard incomplete rows safely.
        return maxContexts <= 0 ? Collections.emptyList()
                : chatMessageMapper.selectRecentListByConversationId(conversation.getId(), maxContexts * 2 + 2);
    }

    private <T> T awaitEnrichment(CompletableFuture<T> future, T fallback, String dependency, String requestId,
                                  AtomicBoolean degraded) {
        try {
            // orTimeout starts when each future is created, so two concurrent dependencies share
            // one wall-clock budget instead of consuming the timeout sequentially.
            return future.orTimeout(PREPROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS).get();
        } catch (Exception ex) {
            log.warn("[ai-chat][requestId={}] {} enrichment timed out or failed; continuing with fallback",
                    requestId, dependency, ex);
            degraded.set(true);
            future.cancel(true);
            return fallback;
        }
    }

    private static long elapsedMs(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static AiChatMessageSendRespVO progressEvent(String requestId, String stage, int completed,
            int total, long elapsedMs, String message) {
        return new AiChatMessageSendRespVO().setEventType("TASK_PROGRESS").setRequestId(requestId)
                .setStage(stage).setCompletedItems(completed).setTotalItems(total)
                .setElapsedMs(elapsedMs).setMessage(message);
    }

    private Prompt buildPrompt(AiChatConversationDO conversation, List<AiChatMessageDO> messages,
                               List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments,
                               AiWebSearchResponse webSearchResponse,
                               AiModelDO model, AiChatMessageSendReqVO sendReqVO,
                               AiChatToolAction requestedAction,
                               AiChatToolExecutionListener toolExecutionListener) {
        List<Message> chatMessages = new ArrayList<>();
        // 1.1 System Context 角色设定
        if (StrUtil.isNotBlank(conversation.getSystemMessage())) {
            chatMessages.add(new SystemMessage(conversation.getSystemMessage()));
        }
        if (requestedAction != null) {
            chatMessages.add(new SystemMessage(buildToolIntentMessage(requestedAction, sendReqVO.getIntentRequestId())));
        }

        // 1.2 历史 history message 历史消息
        List<AiChatMessageDO> contextMessages = filterContextMessages(messages, conversation, sendReqVO);
        contextMessages.forEach(message -> {
            chatMessages.add(AiUtils.buildMessage(message.getType(), message.getContent()));
            UserMessage attachmentUserMessage = buildAttachmentUserMessage(message.getAttachmentUrls());
            if (attachmentUserMessage != null) {
                chatMessages.add(attachmentUserMessage);
            }
            // TODO @芋艿：历史的知识库；历史的搜索，要不要拼接？
        });

        // 1.3 当前 user message 新发送消息
        chatMessages.add(new UserMessage(sendReqVO.getContent()));

        // 1.4 知识库，通过 UserMessage 实现
        if (CollUtil.isNotEmpty(knowledgeSegments)) {
            String reference = knowledgeSegments.stream()
                    .map(segment -> "<Reference>" + segment.getContent() + "</Reference>")
                    .collect(Collectors.joining("\n\n"));
            chatMessages.add(new UserMessage(String.format(KNOWLEDGE_USER_MESSAGE_TEMPLATE, reference)));
        }

        // 1.5 联网搜索，通过 UserMessage 实现
        if (webSearchResponse != null && CollUtil.isNotEmpty(webSearchResponse.getLists())) {
            String webSearch = webSearchResponse.getLists().stream()
                    .map(page -> {
                        String summary = StrUtil.isNotEmpty(page.getSummary()) ?
                                "\nSummary: " + page.getSummary() : "";
                        return "<WebSearch title=\"" + page.getTitle() + "\" url=\"" + page.getUrl() + "\">"
                                + StrUtil.blankToDefault(page.getSummary(), page.getSnippet()) + "</WebSearch>";
                    })
                    .collect(Collectors.joining("\n\n"));
            chatMessages.add(new UserMessage(String.format(WEB_SEARCH_USER_MESSAGE_TEMPLATE, webSearch)));
        }

        // 1.6 附件，通过 UserMessage 实现
        if (CollUtil.isNotEmpty(sendReqVO.getAttachmentUrls())) {
            UserMessage attachmentUserMessage = buildAttachmentUserMessage(sendReqVO.getAttachmentUrls());
            if (attachmentUserMessage != null) {
                chatMessages.add(attachmentUserMessage);
            }
        }

        // 2.1 查询 tool 工具
        List<ToolCallback> toolCallbacks = toolCallbackService.getToolCallbacks(conversation,
                toolActionService.getAvailableActionsByToolName(conversation), toolExecutionListener);
        Map<String,Object> toolContext = CollUtil.isNotEmpty(toolCallbacks) ? identityContextService.buildToolContext(conversation)
                : Map.of();
        // 2.2 构建 ChatOptions 对象
        AiPlatformEnum platform = AiPlatformEnum.validatePlatform(model.getPlatform());
        ChatOptions chatOptions = AiUtils.buildChatOptions(platform, model.getModel(),
                conversation.getTemperature(), conversation.getMaxTokens(),
                toolCallbacks, toolContext);
        return new Prompt(chatMessages, chatOptions);
    }

    private String buildToolIntentMessage(AiChatToolAction action, UUID intentRequestId) {
        StringBuilder message = new StringBuilder("[TrustedToolIntent]\n")
                .append("用户通过可信快捷入口选择了「").append(action.getLabel()).append("」。")
                .append("请优先考虑调用 ").append(action.getToolName())
                .append("，但如果该工具不适合，可以改用其他已授权工具或追问缺失参数。")
                .append("不要向用户暴露内部工具名。");
        if (intentRequestId != null) {
            message.append("本次请求的可信标识为 ").append(intentRequestId)
                    .append("；如工具支持幂等键，必须将该标识作为幂等键。");
        }
        return message.append("\n[/TrustedToolIntent]").toString();
    }

    private void validateIntentRequestId(AiChatToolAction action, AiChatMessageSendReqVO sendReqVO) {
        if (action != null && action.getRiskLevel() == AiChatToolRiskLevel.ASSET_WRITE
                && sendReqVO.getIntentRequestId() == null) {
            throw exception(CHAT_TOOL_INTENT_REQUEST_ID_REQUIRED);
        }
    }

    private AiChatMessageSendRespVO toToolExecutionResponse(AiChatToolExecutionEvent event) {
        return new AiChatMessageSendRespVO().setEventType(event.getEventType())
                .setToolExecution(new AiChatMessageSendRespVO.ToolExecution()
                        .setExecutionId(event.getExecutionId()).setToolName(event.getToolName())
                        .setIntent(event.getIntent()).setLabel(event.getLabel())
                        .setStatus(event.getStatus()).setMessage(event.getMessage()));
    }

    private void collectBlocks(AtomicReference<List<Map<String, Object>>> blocks,
                               AiChatToolExecutionEvent event) {
        if (!"TOOL_SUCCEEDED".equals(event.getEventType()) || StrUtil.isEmpty(event.getResultPayload())
                || CollUtil.isEmpty(blockResolvers)) {
            return;
        }
        for (AiChatMessageBlockResolver resolver : blockResolvers) {
            if (!resolver.supports(event.getToolName())) {
                continue;
            }
            try {
                List<Map<String, Object>> resolved = resolver.resolve(event.getToolName(), event.getResultPayload());
                if (CollUtil.isNotEmpty(resolved)) {
                    List<Map<String, Object>> merged = new ArrayList<>(blocks.get());
                    merged.addAll(resolved);
                    blocks.set(Collections.unmodifiableList(merged));
                }
            } catch (RuntimeException ex) {
                log.warn("[collectBlocks][toolName({}) resolve structured blocks failed]", event.getToolName(), ex);
            }
            return;
        }
    }

    /**
     * 从历史消息中，获得倒序的 n 组消息作为消息上下文
     * <p>
     * n 组：指的是 user + assistant 形成一组
     *
     * @param messages     消息列表
     * @param conversation 对话
     * @param sendReqVO    发送请求
     * @return 消息上下文
     */
    private List<AiChatMessageDO> filterContextMessages(List<AiChatMessageDO> messages,
            AiChatConversationDO conversation,
            AiChatMessageSendReqVO sendReqVO) {
        if (conversation.getMaxContexts() == null || ObjUtil.notEqual(sendReqVO.getUseContext(), Boolean.TRUE)) {
            return Collections.emptyList();
        }
        List<AiChatMessageDO> contextMessages = new ArrayList<>(conversation.getMaxContexts() * 2);
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessageDO assistantMessage = CollUtil.get(messages, i);
            if (assistantMessage == null || assistantMessage.getReplyId() == null) {
                continue;
            }
            AiChatMessageDO userMessage = CollUtil.get(messages, i - 1);
            if (userMessage == null
                    || ObjUtil.notEqual(assistantMessage.getReplyId(), userMessage.getId())
                    || StrUtil.isEmpty(assistantMessage.getContent())) {
                continue;
            }
            // 由于后续要 reverse 反转，所以先添加 assistantMessage
            contextMessages.add(assistantMessage);
            contextMessages.add(userMessage);
            // 超过最大上下文，结束
            if (contextMessages.size() >= conversation.getMaxContexts() * 2) {
                break;
            }
        }
        Collections.reverse(contextMessages);
        return contextMessages;
    }

    private UserMessage buildAttachmentUserMessage(List<String> attachmentUrls) {
        if (CollUtil.isEmpty(attachmentUrls)) {
            return null;
        }
        // 读取文件内容
        Map<String, String> attachmentContents = Maps.newLinkedHashMapWithExpectedSize(attachmentUrls.size());
        for (String attachmentUrl : attachmentUrls) {
            try {
                String name = FileNameUtil.getName(attachmentUrl);
                String mineType = FileTypeUtils.getMineType(name);
                String content;
                if (FileTypeUtils.isImage(mineType)) {
                    // 特殊：图片则转为 Base64
                    byte[] bytes = HttpUtil.downloadBytes(attachmentUrl);
                    content = Base64.encode(bytes);
                } else {
                    content = knowledgeDocumentService.readUrl(attachmentUrl);
                }
                if (StrUtil.isNotEmpty(content)) {
                    attachmentContents.put(name, content);
                }
            } catch (Exception e) {
                log.error("[buildAttachmentUserMessage][读取附件({}) 发生异常]", attachmentUrl, e);
            }
        }
        if (CollUtil.isEmpty(attachmentContents)) {
            return null;
        }

        // 拼接 UserMessage 消息
        String attachment = attachmentContents.entrySet().stream()
                .map(entry -> "<Attachment name=\"" + entry.getKey() + "\">" + entry.getValue() + "</Attachment>")
                .collect(Collectors.joining("\n\n"));
        return new UserMessage(String.format(Attachment_USER_MESSAGE_TEMPLATE, attachment));
    }

    /**
     * Image-only messages are a supported mobile use case. Keep the request
     * contract backwards compatible by supplying a safe prompt server-side;
     * reject an actually empty message before it reaches a model provider.
     */
    private void normalizeAttachmentOnlyMessage(AiChatMessageSendReqVO request) {
        if (StrUtil.isBlank(request.getContent()) && CollUtil.isNotEmpty(request.getAttachmentUrls())) {
            request.setContent("请分析我上传的图片，并给出购买建议。");
        }
        if (StrUtil.isBlank(request.getContent())) {
            throw new IllegalArgumentException("聊天内容或附件不能为空");
        }
        if (CollUtil.size(request.getAttachmentUrls()) > 3) {
            throw new IllegalArgumentException("最多上传 3 个附件");
        }
    }

    private AiChatMessageDO createChatMessage(Long conversationId, Long replyId,
                                              AiModelDO model, Long userId, Long roleId,
                                              MessageType messageType, String content, Boolean useContext,
                                              List<AiKnowledgeSegmentSearchRespBO> knowledgeSegments,
                                              List<String> attachmentUrls,
                                              AiWebSearchResponse webSearchResponse) {
        AiChatMessageDO message = new AiChatMessageDO().setConversationId(conversationId).setReplyId(replyId)
                .setModel(model.getModel()).setModelId(model.getId()).setUserId(userId).setRoleId(roleId)
                .setType(messageType.getValue()).setContent(content).setUseContext(useContext)
                .setSegmentIds(convertList(knowledgeSegments, AiKnowledgeSegmentSearchRespBO::getId))
                .setAttachmentUrls(attachmentUrls);
        if (webSearchResponse != null) {
            message.setWebSearchPages(webSearchResponse.getLists());
        }
        message.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    @Override
    public List<AiChatMessageDO> getChatMessageListByConversationId(Long conversationId) {
        return chatMessageMapper.selectListByConversationId(conversationId);
    }

    @Override
    public void deleteChatMessage(Long id, Long userId) {
        // 1. 校验消息存在
        AiChatMessageDO message = chatMessageMapper.selectById(id);
        if (message == null || ObjUtil.notEqual(message.getUserId(), userId)) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteById(id);
    }

    @Override
    public void deleteChatMessageByConversationId(Long conversationId, Long userId) {
        // 1. 校验消息存在
        List<AiChatMessageDO> messages = chatMessageMapper.selectListByConversationId(conversationId);
        if (CollUtil.isEmpty(messages) || ObjUtil.notEqual(messages.get(0).getUserId(), userId)) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteByIds(convertList(messages, AiChatMessageDO::getId));
    }

    @Override
    public void deleteChatMessageByAdmin(Long id) {
        // 1. 校验消息存在
        AiChatMessageDO message = chatMessageMapper.selectById(id);
        if (message == null) {
            throw exception(CHAT_MESSAGE_NOT_EXIST);
        }
        // 2. 执行删除
        chatMessageMapper.deleteById(id);
    }

    @Override
    public Map<Long, Integer> getChatMessageCountMap(Collection<Long> conversationIds) {
        return chatMessageMapper.selectCountMapByConversationId(conversationIds);
    }

    @Override
    public PageResult<AiChatMessageDO> getChatMessagePage(AiChatMessagePageReqVO pageReqVO) {
        return chatMessageMapper.selectPage(pageReqVO);
    }

}
