package com.qiji.cps.module.ai.service.capability;

import com.qiji.cps.module.ai.dal.dataobject.chat.AiChatConversationDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiCapabilitySnapshotTest {
    @Test void unversionedSnapshotMustFailClosed() {
        assertThrows(IllegalStateException.class, () -> AiCapabilitySnapshotService.read(
                new AiChatConversationDO().setEffectiveCapabilitySnapshot("{\"tools\":[]}")));
    }
    @Test void malformedSnapshotMustFailClosed() {
        assertThrows(IllegalStateException.class, () -> AiCapabilitySnapshotService.read(
                new AiChatConversationDO().setEffectiveCapabilitySnapshot("not-json")));
    }
    @Test void legacyConversationHasNoSnapshot() {
        assertNull(AiCapabilitySnapshotService.read(new AiChatConversationDO()));
    }
}
