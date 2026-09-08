package com.qiji.cps.module.ai.controller.admin.chat.vo.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiChatMessageSendRespVOTest {

    @Test
    void taskEvent_exposesSafeProgressMetadata() {
        AiChatMessageSendRespVO response = new AiChatMessageSendRespVO()
                .setEventType("TASK_PROGRESS")
                .setRequestId("request-1")
                .setStage("RETRIEVING")
                .setCompletedItems(1)
                .setTotalItems(2)
                .setElapsedMs(300L)
                .setMessage("正在检索资料");

        assertEquals("TASK_PROGRESS", response.getEventType());
        assertEquals("request-1", response.getRequestId());
        assertEquals("RETRIEVING", response.getStage());
        assertEquals(1, response.getCompletedItems());
        assertEquals(2, response.getTotalItems());
        assertEquals(300L, response.getElapsedMs());
    }
}
