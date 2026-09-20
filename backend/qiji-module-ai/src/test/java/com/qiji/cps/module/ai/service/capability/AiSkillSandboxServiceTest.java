package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.dataobject.model.AiModelDO;
import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import com.qiji.cps.module.ai.dal.mysql.capability.AiSkillVersionMapper;
import com.qiji.cps.module.ai.service.model.AiModelService;
import com.qiji.cps.module.ai.enums.model.AiModelTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSkillSandboxServiceTest extends BaseMockitoUnitTest {
    @InjectMocks private AiSkillSandboxService sandbox;
    @Mock private AiSkillService skills;
    @Mock private AiModelService models;
    @Mock private AiCapabilityRuntime runtime;
    @Mock private AiSkillVersionMapper versions;

    @Test void previewCannotTriggerModelOrTools() {
        var request = new AiSkillTestReqVO().setSkillVersionId(1L);
        var preview = new AiSkillTestRespVO().setExecutionMode("READ_ONLY_PREVIEW");
        when(skills.test(request)).thenReturn(preview);
        assertSame(preview,sandbox.test(request));
        verifyNoInteractions(models,runtime,versions);
    }

    @Test void executionUsesExactDraftAndNeverBindsMemberOrMutation() {
        var request = new AiSkillTestReqVO().setSkillVersionId(1L).setExecuteModel(true).setInput("测试选品");
        when(skills.test(request)).thenReturn(new AiSkillTestRespVO().setSystemPrompt("draft prompt")
                .setBindings(List.of(new AiSkillBindingRespVO().setBindingType("LOCAL_TOOL").setLocalToolName("search"))));
        var version = new AiSkillVersionDO().setId(1L).setSkillId(2L).setVersion(7).setPublishStatus("DRAFT");
        when(versions.selectById(1L)).thenReturn(version);
        when(skills.versions(2L)).thenReturn(List.of(version));
        when(skills.get(2L)).thenReturn(new AiSkillDO().setId(2L).setCode("test-skill"));
        when(runtime.callbacks(any())).thenReturn(List.of());
        when(models.getRequiredDefaultModel(AiModelTypeEnum.CHAT.getType())).thenReturn(new AiModelDO()
                .setId(3L).setType(AiModelTypeEnum.CHAT.getType()).setPlatform("OpenAI").setModel("test")
                .setTemperature(0.2).setMaxTokens(512));
        ChatModel model = mock(ChatModel.class);
        when(models.getChatModel(3L)).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("result")))));
        assertEquals("result",sandbox.test(request).getOutput());
        var captor = ArgumentCaptor.forClass(AiChatConversationDO.class);
        verify(runtime).callbacks(captor.capture());
        assertNull(captor.getValue().getMemberId());
        var snapshot = AiCapabilitySnapshotService.read(captor.getValue());
        assertFalse(snapshot.getAllowMutation());
        assertEquals("READ_ONLY",snapshot.getTools().get(0).getRiskLevel());
        assertEquals(7,snapshot.getTools().get(0).getSkillVersion());
    }
}
