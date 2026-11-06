package com.qiji.cps.module.cps.service.publish;

import com.qiji.cps.module.cps.controller.admin.publish.vo.CpsPublishDispatchRespVO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishLogDO;
import com.qiji.cps.module.cps.dal.dataobject.publish.CpsPublishTaskDO;
import com.qiji.cps.module.cps.dal.mysql.publish.CpsPublishLogMapper;
import com.qiji.cps.module.cps.dal.mysql.publish.CpsPublishTaskMapper;
import com.qiji.cps.module.cps.dal.mysql.rebate.CpsRebateAccountMapper;
import com.qiji.cps.module.cps.service.publish.channel.CpsPublishChannel;
import com.qiji.cps.module.cps.service.publish.channel.CpsPublishSendContext;
import com.qiji.cps.module.cps.service.publish.channel.CpsPublishSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CpsPublishTaskServiceImplTest {

    @InjectMocks
    private CpsPublishTaskServiceImpl service;

    @Mock
    private CpsPublishTaskMapper publishTaskMapper;

    @Mock
    private CpsPublishLogMapper publishLogMapper;

    @Mock
    private CpsRebateAccountMapper rebateAccountMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock redissonLock;

    private RecordingChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, CpsPublishLogDO.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, CpsPublishTaskDO.class);
        channel = new RecordingChannel();
        when(redissonClient.getLock(anyString())).thenReturn(redissonLock);
        Field field = CpsPublishTaskServiceImpl.class.getDeclaredField("publishChannels");
        field.setAccessible(true);
        field.set(service, List.of(channel));
        service.init();
    }

    @Test
    @DisplayName("dispatchNow - 渲染模板并逐个会员投放，写入投放记录")
    void dispatchNow_rendersTemplateAndWritesLog() {
        CpsPublishTaskDO task = buildTask();
        task.setTargetType(CpsPublishConstants.TargetType.SPECIFIED);
        task.setTargetIds("101, 102");
        task.setContentTemplate("你好 {member_id}，{task_name} 来了 {url}");
        task.setLinkTemplate("https://cps.example.com/p/101");
        stubTask(task);

        CpsPublishDispatchRespVO result = service.dispatchNow(task.getId());

        assertEquals(2, result.getSuccessCount().intValue());
        assertEquals(0, result.getFailedCount().intValue());
        assertEquals(2, channel.contexts.size());
        CpsPublishSendContext first = channel.contexts.get(0);
        assertEquals(101L, first.memberId().longValue());
        assertEquals("https://cps.example.com/p/101", first.linkUrl());
        assertTrue(first.content().contains("你好 101"));
        assertTrue(first.content().contains("https://cps.example.com/p/101"));

        ArgumentCaptor<CpsPublishLogDO> captor = ArgumentCaptor.forClass(CpsPublishLogDO.class);
        verify(publishLogMapper, times(2)).insert(captor.capture());
        assertEquals(CpsPublishConstants.LogStatus.PROCESSING, captor.getAllValues().get(0).getStatus());
        verify(publishTaskMapper).updateDispatchState(eq(task.getId()), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("dispatchNow - 超过每日频次上限时跳过投放")
    void dispatchNow_skipsWhenFrequencyLimitReached() {
        CpsPublishTaskDO task = buildTask();
        task.setTargetType(CpsPublishConstants.TargetType.SPECIFIED);
        task.setTargetIds("101");
        task.setFrequencyLimitPerDay(1);
        stubTask(task);
        when(publishLogMapper.countByTaskAndMemberSince(eq(task.getId()), eq(101L), any(LocalDateTime.class)))
                .thenReturn(1L);

        CpsPublishDispatchRespVO result = service.dispatchNow(task.getId());

        assertEquals(0, result.getSuccessCount().intValue());
        assertEquals(1, result.getSkippedCount().intValue());
        assertTrue(channel.contexts.isEmpty());
    }

    @Test
    @DisplayName("dispatchNow - 免打扰时段内手工触发仍然投放")
    void dispatchNow_ignoresQuietHours() {
        CpsPublishTaskDO task = buildTask();
        task.setTargetType(CpsPublishConstants.TargetType.SPECIFIED);
        task.setTargetIds("101");
        task.setQuietStartTime(LocalTime.now().minusMinutes(10));
        task.setQuietEndTime(LocalTime.now().plusMinutes(10));
        stubTask(task);

        CpsPublishDispatchRespVO result = service.dispatchNow(task.getId());

        assertEquals(1, result.getSuccessCount().intValue());
        assertEquals(1, channel.contexts.size());
    }

    @Test
    @DisplayName("dispatchDueTasks - 免打扰时段内跳过整个任务")
    void dispatchDueTasks_skipsInsideQuietHours() {
        CpsPublishTaskDO task = buildTask();
        task.setTargetType(CpsPublishConstants.TargetType.SPECIFIED);
        task.setTargetIds("101");
        task.setCronExpression("0 0/5 * * * ?");
        task.setNextDispatchTime(LocalDateTime.now().minusMinutes(1));
        task.setQuietStartTime(LocalTime.now().minusMinutes(10));
        task.setQuietEndTime(LocalTime.now().plusMinutes(10));
        stubTask(task);
        when(publishTaskMapper.selectDispatchableList(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));

        CpsPublishDispatchRespVO result = service.dispatchDueTasks();

        assertEquals(0, result.getSuccessCount().intValue());
        assertEquals(1, result.getSkippedCount().intValue());
        assertTrue(channel.contexts.isEmpty());
    }

    @Test
    @DisplayName("dispatchDueTasks - 幂等键冲突时跳过，不重复触达")
    void dispatchDueTasks_skipsDuplicatedIdempotentKey() {
        CpsPublishTaskDO task = buildTask();
        task.setTargetType(CpsPublishConstants.TargetType.SPECIFIED);
        task.setTargetIds("101");
        task.setCronExpression("0 0/5 * * * ?");
        task.setNextDispatchTime(LocalDateTime.now().minusMinutes(1));
        stubTask(task);
        when(publishTaskMapper.selectDispatchableList(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        doThrow(new DuplicateKeyException("duplicate")).when(publishLogMapper).insert(any(CpsPublishLogDO.class));

        CpsPublishDispatchRespVO result = service.dispatchDueTasks();

        assertEquals(0, result.getSuccessCount().intValue());
        assertEquals(1, result.getSkippedCount().intValue());
        assertTrue(channel.contexts.isEmpty());
        verify(publishLogMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("dispatchDueTasks - 渠道异常时记录失败")
    void dispatchDueTasks_recordsFailure() {
        CpsPublishTaskDO task = buildTask();
        task.setTargetType(CpsPublishConstants.TargetType.SPECIFIED);
        task.setTargetIds("101");
        task.setCronExpression("0 0/5 * * * ?");
        task.setNextDispatchTime(LocalDateTime.now().minusMinutes(1));
        stubTask(task);
        when(publishTaskMapper.selectDispatchableList(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(task));
        channel.failure = new IllegalStateException("通道不可用");

        CpsPublishDispatchRespVO result = service.dispatchDueTasks();

        assertEquals(0, result.getSuccessCount().intValue());
        assertEquals(1, result.getFailedCount().intValue());
    }

    private void stubTask(CpsPublishTaskDO task) {
        when(publishTaskMapper.selectById(task.getId())).thenReturn(task);
    }

    private CpsPublishTaskDO buildTask() {
        return CpsPublishTaskDO.builder()
                .id(7L)
                .taskCode("PUB-TEST")
                .taskName("测试投放")
                .sourceType(CpsPublishConstants.SourceType.MANUAL)
                .channelCode(CpsPublishConstants.ChannelCode.CLIPBOARD)
                .targetType(CpsPublishConstants.TargetType.SPECIFIED)
                .contentTemplate("hi {member_id}")
                .status(CpsPublishConstants.TaskStatus.ONLINE)
                .build();
    }

    private static final class RecordingChannel implements CpsPublishChannel {

        private final List<CpsPublishSendContext> contexts = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public String getChannelCode() {
            return CpsPublishConstants.ChannelCode.CLIPBOARD;
        }

        @Override
        public CpsPublishSendResult send(CpsPublishSendContext context) {
            if (failure != null) {
                throw failure;
            }
            contexts.add(context);
            return CpsPublishSendResult.ok();
        }
    }

}
