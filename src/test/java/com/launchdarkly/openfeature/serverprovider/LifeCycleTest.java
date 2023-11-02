package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests in this suite use a real client instance and the public constructor.
 * <p>
 * Detailed provider tests use a mock client to test specific result and context conversions.
 */
public class LifeCycleTest {
    @Test
    public void canCallThePublicConstructor() {
        assertDoesNotThrow(() -> {
            var provider = new Provider("fake-key", new LDConfig.Builder()
                .offline(true).build());
        });
    }

    @Test
    public void canInitializeAnOfflineClient() {
        assertDoesNotThrow(() -> {
            var provider = new Provider("fake-key", new LDConfig.Builder()
                .offline(true).build());
            provider.initialize(new ImmutableContext("context-key"));
            assertEquals(ProviderState.READY, provider.getState());
            var ldClient = provider.getLdClient();
            assertEquals(DataSourceStatusProvider.State.VALID, ldClient.getDataSourceStatusProvider().getStatus().getState());
        });
    }

    @Test
    public void canShutdownAnOfflineClient() {
        assertDoesNotThrow(() -> {
            var provider = new Provider("fake-key", new LDConfig.Builder()
                .offline(true).build());
            provider.initialize(new ImmutableContext("context-key"));
            provider.shutdown();
            // Currently this does not check the provider state as the OF spec doesn't yet have a terminal
            // shutdown state.
            var ldClient = provider.getLdClient();
            assertEquals(DataSourceStatusProvider.State.OFF, ldClient.getDataSourceStatusProvider().getStatus().getState());
        });
    }

    @Test
    public void itEmitsReadyEvents() {
        var provider = new Provider("fake-key", new LDConfig.Builder()
            .offline(true).build());

        var readyCount = new AtomicInteger();
        var errorCount = new AtomicInteger();
        var staleCount = new AtomicInteger();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_READY, (detail) -> {
            readyCount.getAndIncrement();
        });

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_STALE, (detail) -> {
            staleCount.getAndIncrement();
        });

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            errorCount.getAndIncrement();
        });

        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        OpenFeatureAPI.getInstance().shutdown();

        assertEquals(1, readyCount.get());
        assertEquals(0, staleCount.get());
        assertEquals(0, errorCount.get());
    }
}
