package com.qiji.cps.module.cps.mcp.tool;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskSaveReqVO;
import com.qiji.cps.module.cps.dal.mysql.mcp.CpsMcpAccessLogMapper;
import com.qiji.cps.module.cps.mcp.security.CpsMcpAuthorizationService;
import com.qiji.cps.module.cps.service.publish.CpsPublishConstants;
import com.qiji.cps.module.cps.service.publish.CpsPublishTaskService;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@Component("cps_schedule_publish_task")
public class CpsSchedulePublishTaskToolFunction
        implements BiFunction<CpsSchedulePublishTaskToolFunction.Request, ToolContext,
        CpsSchedulePublishTaskToolFunction.Response> {

    private static final Set<String> ALLOWED_CHANNELS = Set.of(
            CpsPublishConstants.ChannelCode.SMS,
            CpsPublishConstants.ChannelCode.NOTIFY,
            CpsPublishConstants.ChannelCode.CLIPBOARD);
    private static final int MAX_RECIPIENTS = 100;

    @Resource
    private CpsPublishTaskService publishTaskService;

    @Resource
    private CpsMcpAccessLogMapper accessLogMapper;

    @Data
    @JsonClassDescription("起草一条仅限指定会员的 CPS 投放任务草稿，不发送、不上线，必须由运营在后台确认")
    public static class Request {
        @JsonProperty(value = "task_name", required = true)
        @JsonPropertyDescription("任务名称")
        private String taskName;

        @JsonProperty(value = "channel_code", required = true)
        @JsonPropertyDescription("触达渠道：SMS 短信 / NOTIFY 站内信 / CLIPBOARD 文案看板")
        private String channelCode;

        @JsonProperty(value = "content_template", required = true)
        @JsonPropertyDescription("消息模板，仅支持 {member_id}、{task_name}、{url} 占位符")
        private String contentTemplate;

        @JsonProperty(value = "member_ids", required = true)
        @JsonPropertyDescription("指定接收会员 ID，逗号分隔，最多 100 个；不接受群发全部会员")
        private String memberIds;

        @JsonProperty(value = "link_template")
        @JsonPropertyDescription("纯 HTTP(S) 链接，不接受会员归因占位符")
        private String linkTemplate;

        @JsonProperty(value = "template_code")
        @JsonPropertyDescription("渠道模板编码，短信 / 站内信模板使用")
        private String templateCode;

        @JsonProperty(value = "cron_expression")
        @JsonPropertyDescription("Cron 表达式，Spring 格式（秒 分 时 日 月 周），留空表示只手工触发")
        private String cronExpression;

        @JsonProperty(value = "frequency_limit_per_day")
        @JsonPropertyDescription("每个会员每日最大投放次数，0 表示不限制")
        private Integer frequencyLimitPerDay;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String status;
        private String reason;
        private String source;
        private Long taskId;
        private String taskCode;
    }

    @Override
    public Response apply(Request request, ToolContext toolContext) {
        long startedAt = System.currentTimeMillis();
        Long tenantId = contextLong(toolContext, CpsMcpAuthorizationService.TOOL_CONTEXT_TENANT_ID);
        if (tenantId == null || tenantId <= 0) {
            Response response = new Response("UNTRUSTED_IDENTITY", "缺少可信租户上下文", "publish_task", null, null);
            CpsMcpToolAuditSupport.record(accessLogMapper, "cps_schedule_publish_task", request, response,
                    new SecurityException("trusted tenant required"), toolContext, startedAt);
            return response;
        }
        String invalid = validate(request);
        if (invalid != null) {
            Response response = new Response("INVALID_ARGUMENT", invalid, "publish_task", null, null);
            CpsMcpToolAuditSupport.record(accessLogMapper, "cps_schedule_publish_task", request, response,
                    new IllegalArgumentException(invalid), toolContext, startedAt);
            return response;
        }
        try {
            CpsPublishTaskSaveReqVO reqVO = new CpsPublishTaskSaveReqVO();
            reqVO.setTaskName(request.getTaskName().trim());
            reqVO.setChannelCode(request.getChannelCode());
            reqVO.setContentTemplate(request.getContentTemplate());
            reqVO.setSourceType(CpsPublishConstants.SourceType.MANUAL);
            reqVO.setLinkTemplate(request.getLinkTemplate());
            reqVO.setTemplateCode(request.getTemplateCode());
            reqVO.setCronExpression(request.getCronExpression());
            reqVO.setFrequencyLimitPerDay(request.getFrequencyLimitPerDay());
            reqVO.setTargetType(CpsPublishConstants.TargetType.SPECIFIED);
            reqVO.setTargetIds(request.getMemberIds().replace(" ", ""));
            Long taskId = publishTaskService.createTask(reqVO);
            var task = publishTaskService.getTask(taskId);
            Response response = new Response("SUCCESS", "投放任务草稿已创建，未发送也未上线",
                    "publish_task", taskId, task == null ? null : task.getTaskCode());
            CpsMcpToolAuditSupport.record(accessLogMapper, "cps_schedule_publish_task", request, response, null, toolContext, startedAt);
            return response;
        } catch (Exception e) {
            Response response = new Response("FAILED", "投放任务创建失败：" + CpsMcpToolAuditSupport.sanitizeError(e.getMessage()),
                    "publish_task", null, null);
            CpsMcpToolAuditSupport.record(accessLogMapper, "cps_schedule_publish_task", request, response, e, toolContext, startedAt);
            return response;
        }
    }

    private String validate(Request request) {
        if (request == null || !StringUtils.hasText(request.getTaskName())
                || !StringUtils.hasText(request.getChannelCode())
                || !StringUtils.hasText(request.getContentTemplate())) {
            return "task_name、channel_code、content_template 不能为空";
        }
        if (request.getTaskName().length() > 128 || request.getContentTemplate().length() > 10000) {
            return "task_name 或 content_template 超长";
        }
        if (!ALLOWED_CHANNELS.contains(request.getChannelCode())) {
            return "不支持的触达渠道";
        }
        if (!CpsPublishConstants.ChannelCode.CLIPBOARD.equals(request.getChannelCode())
                && !StringUtils.hasText(request.getTemplateCode())) {
            return "该渠道必须提供 template_code";
        }
        if (StringUtils.hasText(request.getTemplateCode()) && request.getTemplateCode().length() > 64) {
            return "template_code 超长";
        }
        if (StringUtils.hasText(request.getLinkTemplate())) {
            if (request.getLinkTemplate().length() > 2048
                    || !(request.getLinkTemplate().startsWith("https://") || request.getLinkTemplate().startsWith("http://"))
                    || request.getLinkTemplate().contains("{") || request.getLinkTemplate().contains("}")) {
                return "link_template 必须是纯 HTTP(S) 链接，不接受归因占位符";
            }
        }
        if (StringUtils.hasText(request.getCronExpression())) {
            if (request.getCronExpression().length() > 128 || !CronExpression.isValidExpression(request.getCronExpression())) {
                return "cron_expression 非法";
            }
        }
        if (request.getFrequencyLimitPerDay() != null && request.getFrequencyLimitPerDay() < 0) {
            return "frequency_limit_per_day 不能为负数";
        }
        List<Long> ids = new ArrayList<>();
        if (!StringUtils.hasText(request.getMemberIds())) {
            return "member_ids 不能为空";
        }
        for (String raw : request.getMemberIds().split(",", -1)) {
            try {
                long id = Long.parseLong(raw.trim());
                if (id <= 0) {
                    return "member_ids 必须是正整数";
                }
                ids.add(id);
            } catch (NumberFormatException ex) {
                return "member_ids 必须是正整数";
            }
        }
        return ids.size() > MAX_RECIPIENTS ? "member_ids 最多 " + MAX_RECIPIENTS + " 个" : null;
    }

    private Long contextLong(ToolContext toolContext, String key) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> context = toolContext.getContext();
        if (context == null || context.get(key) == null) {
            return null;
        }
        Object value = context.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
