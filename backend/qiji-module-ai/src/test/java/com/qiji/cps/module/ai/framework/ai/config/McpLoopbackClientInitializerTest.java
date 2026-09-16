package com.qiji.cps.module.ai.framework.ai.config;

import com.qiji.cps.framework.test.core.ut.BaseMockitoUnitTest;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpLoopbackClientInitializerTest extends BaseMockitoUnitTest {

    @Mock
    private McpSyncClient cpsClient;
    @Mock
    private McpSyncClient filesystemClient;

    private McpLoopbackClientInitializer initializer;

    @BeforeEach
    void setUp() {
        QijiAiProperties properties = new QijiAiProperties();
        properties.getMcp().getLoopbackClient().setEnabled(true);
        properties.getMcp().getLoopbackClient().setConnectionName("cps");
        initializer = new McpLoopbackClientInitializer(properties, List.of(cpsClient, filesystemClient));
    }

    @Test
    void onApplicationReady_initializesOnlyTheConfiguredUninitializedLoopbackClient() {
        when(cpsClient.getClientInfo()).thenReturn(McpSchema.Implementation.builder("mcp - cps", "1").build());
        when(cpsClient.isInitialized()).thenReturn(false);

        initializer.onApplicationReady(null);

        verify(cpsClient).initialize();
        verify(filesystemClient, never()).initialize();
    }

    @Test
    void onApplicationReady_skipsTheConfiguredClientWhenItIsAlreadyInitialized() {
        when(cpsClient.getClientInfo()).thenReturn(McpSchema.Implementation.builder("mcp - cps", "1").build());
        when(cpsClient.isInitialized()).thenReturn(true);

        initializer.onApplicationReady(null);

        verify(cpsClient, never()).initialize();
        verify(filesystemClient, never()).initialize();
    }

    @Test
    void onApplicationReady_allowsTheConfiguredLoopbackClientToBeAbsent() {
        when(filesystemClient.getClientInfo()).thenReturn(McpSchema.Implementation.builder("mcp - filesystem", "1").build());
        McpLoopbackClientInitializer missingClientInitializer = new McpLoopbackClientInitializer(
                createEnabledProperties(), List.of(filesystemClient));

        assertDoesNotThrow(() -> missingClientInitializer.onApplicationReady(null));

        verify(filesystemClient, never()).initialize();
    }

    @Test
    void onApplicationReady_skipsInitializationWhenLoopbackIsDisabled() {
        QijiAiProperties properties = new QijiAiProperties();
        properties.getMcp().getLoopbackClient().setEnabled(false);
        properties.getMcp().getLoopbackClient().setConnectionName("cps");
        McpLoopbackClientInitializer disabledInitializer = new McpLoopbackClientInitializer(
                properties, List.of(cpsClient));

        disabledInitializer.onApplicationReady(null);

        verify(cpsClient, never()).getClientInfo();
        verify(cpsClient, never()).initialize();
    }

    @Test
    void onApplicationReady_skipsInitializationWhenConnectionNameIsBlank() {
        QijiAiProperties properties = new QijiAiProperties();
        properties.getMcp().getLoopbackClient().setEnabled(true);
        properties.getMcp().getLoopbackClient().setConnectionName(" ");
        McpLoopbackClientInitializer blankNameInitializer = new McpLoopbackClientInitializer(
                properties, List.of(cpsClient));

        blankNameInitializer.onApplicationReady(null);

        verify(cpsClient, never()).getClientInfo();
        verify(cpsClient, never()).initialize();
    }

    private static QijiAiProperties createEnabledProperties() {
        QijiAiProperties properties = new QijiAiProperties();
        properties.getMcp().getLoopbackClient().setEnabled(true);
        properties.getMcp().getLoopbackClient().setConnectionName("cps");
        return properties;
    }

}
