package com.qiji.cps.module.cps.mcp.tool;

import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishTaskSaveReqVO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;
import com.qiji.cps.module.cps.dal.mysql.mcp.CpsMcpAccessLogMapper;
import com.qiji.cps.module.cps.service.publish.CpsPublishConstants;
import com.qiji.cps.module.cps.service.publish.CpsPublishTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CpsPublishMcpToolFunctionTest {

    private CpsPublishTaskService publishTaskService;
    private CpsSchedulePublishTaskToolFunction scheduleTool;
    private CpsListPublishTasksToolFunction listTool;
    private final ToolContext trusted = new ToolContext(Map.of("TENANT_ID", 1L, "ACTOR_USER_ID", 9L));

    @BeforeEach
    void setUp() {
        publishTaskService = mock(CpsPublishTaskService.class);
        CpsMcpAccessLogMapper accessLogMapper = mock(CpsMcpAccessLogMapper.class);
        scheduleTool = new CpsSchedulePublishTaskToolFunction();
        ReflectionTestUtils.setField(scheduleTool, "publishTaskService", publishTaskService);
        ReflectionTestUtils.setField(scheduleTool, "accessLogMapper", accessLogMapper);
        listTool = new CpsListPublishTasksToolFunction();
        ReflectionTestUtils.setField(listTool, "publishTaskService", publishTaskService);
        ReflectionTestUtils.setField(listTool, "accessLogMapper", accessLogMapper);
    }

    @Test
    void scheduleRejectsMissingTrustedTenant() {
        var response = scheduleTool.apply(validRequest(), null);
        assertEquals("UNTRUSTED_IDENTITY", response.getStatus());
        verify(publishTaskService, never()).createTask(any());
    }

    @Test
    void scheduleRejectsBlankOrExcessRecipients() {
        var request = validRequest();
        request.setMemberIds(" ");
        assertEquals("INVALID_ARGUMENT", scheduleTool.apply(request, trusted).getStatus());
        request.setMemberIds("1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,"
                + "26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,"
                + "51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74,75,"
                + "76,77,78,79,80,81,82,83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100,101");
        assertEquals("INVALID_ARGUMENT", scheduleTool.apply(request, trusted).getStatus());
        request.setMemberIds("0");
        assertEquals("INVALID_ARGUMENT", scheduleTool.apply(request, trusted).getStatus());
        verify(publishTaskService, never()).createTask(any());
    }

    @Test
    void scheduleRejectsInvalidCronAndAttributionLink() {
        var request = validRequest();
        request.setCronExpression("not a cron");
        assertEquals("INVALID_ARGUMENT", scheduleTool.apply(request, trusted).getStatus());
        request.setCronExpression("0 0 9 * * *");
        request.setLinkTemplate("https://example.com/p/{member_id}");
        assertEquals("INVALID_ARGUMENT", scheduleTool.apply(request, trusted).getStatus());
        verify(publishTaskService, never()).createTask(any());
    }

    @Test
    void scheduleCreatesManualSpecifiedDraftOnly() {
        when(publishTaskService.createTask(any())).thenReturn(5L);
        when(publishTaskService.getTask(5L)).thenReturn(CpsPublishTaskDO.builder().id(5L).taskCode("PUB-5").build());

        var response = scheduleTool.apply(validRequest(), trusted);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("PUB-5", response.getTaskCode());
        ArgumentCaptor<CpsPublishTaskSaveReqVO> captor = ArgumentCaptor.forClass(CpsPublishTaskSaveReqVO.class);
        verify(publishTaskService).createTask(captor.capture());
        assertEquals(CpsPublishConstants.SourceType.MANUAL, captor.getValue().getSourceType());
        assertEquals(CpsPublishConstants.TargetType.SPECIFIED, captor.getValue().getTargetType());
        assertEquals("101,102", captor.getValue().getTargetIds());
        assertNull(captor.getValue().getSourceId());
    }

    @Test
    void listRejectsMissingTrustedTenant() {
        var response = listTool.apply(new CpsListPublishTasksToolFunction.Request(), new ToolContext(Map.of()));
        assertEquals("UNTRUSTED_IDENTITY", response.getStatus());
        verify(publishTaskService, never()).getTaskPage(any());
    }

    private CpsSchedulePublishTaskToolFunction.Request validRequest() {
        var request = new CpsSchedulePublishTaskToolFunction.Request();
        request.setTaskName("周末活动");
        request.setChannelCode(CpsPublishConstants.ChannelCode.CLIPBOARD);
        request.setContentTemplate("你好 {member_id}");
        request.setMemberIds("101, 102");
        return request;
    }
}
