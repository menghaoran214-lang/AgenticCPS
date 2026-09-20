package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import com.qiji.cps.module.ai.controller.admin.capability.vo.*;
import com.qiji.cps.module.ai.dal.dataobject.capability.*;
import com.qiji.cps.module.ai.dal.mysql.capability.*;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiSkillServiceImplTest extends BaseMockitoUnitTest {
    @InjectMocks private AiSkillServiceImpl service;
    @Mock private AiSkillMapper skillMapper;
    @Mock private AiSkillVersionMapper versionMapper;
    @Mock private AiSkillBindingMapper bindingMapper;

    @Test void missingVersionCannotBeSilentlyPublished() {
        assertThrows(RuntimeException.class, () -> service.publishVersion(99L));
        verifyNoInteractions(skillMapper);
    }

    @Test void publishedVersionContentCannotBeEdited() {
        lockedVersion("PUBLISHED", 0L);
        AiSkillVersionSaveReqVO request = new AiSkillVersionSaveReqVO();
        request.setId(3L);
        request.setSkillId(1L);
        request.setSystemPrompt("changed");
        assertThrows(RuntimeException.class, () -> service.updateVersion(request));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void draftCannotBePublishedWithoutReview() {
        lockedVersion("DRAFT", 0L);
        assertThrows(RuntimeException.class, () -> service.publishVersion(3L));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void rollbackCannotPublishAnUnreviewedDraft() {
        when(skillMapper.selectForUpdate(1L)).thenReturn(new AiSkillDO().setId(1L));
        when(versionMapper.selectBySkillIdAndVersion(1L, 2)).thenReturn(new AiSkillVersionDO()
                .setId(3L).setSkillId(1L).setVersion(2).setPublishStatus("DRAFT"));
        assertThrows(RuntimeException.class, () -> service.rollbackVersion(1L, 2));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void staleDraftRevisionCannotOverwriteNewerContent() {
        lockedVersion("DRAFT", 5L);
        var request = new AiSkillVersionSaveReqVO().setId(3L).setSkillId(1L).setExpectedRevision(4L)
                .setSystemPrompt("changed");
        assertThrows(RuntimeException.class, () -> service.updateVersion(request));
        verify(versionMapper, never()).updateById(any(AiSkillVersionDO.class));
    }

    @Test void publishingReviewedVersionUpdatesCurrentPointer() {
        lockedVersion("REVIEWING", 2L);
        when(skillMapper.selectById(1L)).thenReturn(new AiSkillDO().setId(1L).setStatus(0));
        when(bindingMapper.selectListByVersionId(3L)).thenReturn(java.util.List.of());
        service.publishVersion(3L);
        verify(versionMapper).updateById(argThat((AiSkillVersionDO value) ->
                "PUBLISHED".equals(value.getPublishStatus()) && value.getRevision() == 3));
        verify(skillMapper).updateById(argThat((AiSkillDO value) -> value.getCurrentVersion() == 1));
    }

    private void lockedVersion(String status, Long revision) {
        var version = new AiSkillVersionDO().setId(3L).setSkillId(1L).setVersion(1)
                .setPublishStatus(status).setRevision(revision);
        when(versionMapper.selectById(3L)).thenReturn(version);
        when(skillMapper.selectForUpdate(1L)).thenReturn(new AiSkillDO().setId(1L));
        when(versionMapper.selectForUpdate(3L)).thenReturn(version);
    }
}
