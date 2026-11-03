package com.qiji.cps.module.cps.mcp.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskPageReqVO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;
import com.qiji.cps.module.cps.dal.mysql.mcp.CpsMcpAccessLogMapper;
import com.qiji.cps.module.cps.mcp.security.CpsMcpAuthorizationService;
import com.qiji.cps.module.cps.service.publish.CpsPublishTaskService;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

@Component("cps_list_publish_tasks")
public class CpsListPublishTasksToolFunction
        implements BiFunction<CpsListPublishTasksToolFunction.Request, ToolContext,
        CpsListPublishTasksToolFunction.Response> {

    @Resource
    private CpsPublishTaskService publishTaskService;

    @Resource
    private CpsMcpAccessLogMapper accessLogMapper;

    @Data
    @JsonClassDescription("查询 CPS 投放任务看板，可按状态与触达渠道筛选")
    public static class Request {
        @JsonProperty(value = "status")
        @JsonPropertyDescription("任务状态：DRAFT 草稿 / ONLINE 已上线 / OFFLINE 已下线")
        private String status;

        @JsonProperty(value = "channel_code")
        @JsonPropertyDescription("触达渠道：SMS 短信 / NOTIFY 站内信 / CLIPBOARD 文案看板")
        private String channelCode;

        @JsonProperty(value = "task_name")
        @JsonPropertyDescription("任务名称关键词")
        private String taskName;

        @JsonProperty(value = "page_size")
        @JsonPropertyDescription("分页大小，默认 20，最大 100")
        private Integer pageSize;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String status;
        private String reason;
        private String source;
        private Long total;
        private List<Task> tasks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Task {
        private Long id;
        private String taskCode;
        private String taskName;
        private String sourceType;
        private String channelCode;
        private String targetType;
        private String status;
        private String cronExpression;
        private String lastDispatchTime;
        private String nextDispatchTime;
    }

    @Override
    public Response apply(Request request, ToolContext toolContext) {
        long startedAt = System.currentTimeMillis();
        if (contextLong(toolContext, CpsMcpAuthorizationService.TOOL_CONTEXT_TENANT_ID) == null) {
            Response response = new Response("UNTRUSTED_IDENTITY", "缺少可信租户上下文", "publish_task", 0L, List.of());
            CpsMcpToolAuditSupport.record(accessLogMapper, "cps_list_publish_tasks", request, response,
                    new SecurityException("trusted tenant required"), toolContext, startedAt);
            return response;
        }
        try {
            CpsPublishTaskPageReqVO pageReqVO = new CpsPublishTaskPageReqVO();
            pageReqVO.setStatus(request == null ? null : request.getStatus());
            pageReqVO.setChannelCode(request == null ? null : request.getChannelCode());
            pageReqVO.setTaskName(request == null ? null : request.getTaskName());
            pageReqVO.setPageNo(1);
            pageReqVO.setPageSize(normalizePageSize(request == null ? null : request.getPageSize()));

            var pageResult = publishTaskService.getTaskPage(pageReqVO);
            List<Task> tasks = pageResult.getList().stream().map(this::toTask).toList();
            Response response = new Response("SUCCESS", "已返回投放任务看板", "publish_task",
                    pageResult.getTotal(), tasks);
            CpsMcpToolAuditSupport.record(accessLogMapper, "cps_list_publish_tasks", request, response, null, toolContext, startedAt);
            return response;
        } catch (Exception e) {
            Response response = new Response("FAILED", "投放任务查询失败，请稍后重试", "publish_task", 0L, List.of());
            CpsMcpToolAuditSupport.record(accessLogMapper, "cps_list_publish_tasks", request, response, e, toolContext, startedAt);
            return response;
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private Task toTask(CpsPublishTaskDO task) {
        return new Task(task.getId(), task.getTaskCode(), task.getTaskName(), task.getSourceType(),
                task.getChannelCode(), task.getTargetType(), task.getStatus(), task.getCronExpression(),
                task.getLastDispatchTime() == null ? null : task.getLastDispatchTime().toString(),
                task.getNextDispatchTime() == null ? null : task.getNextDispatchTime().toString());
    }

    private Long contextLong(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
