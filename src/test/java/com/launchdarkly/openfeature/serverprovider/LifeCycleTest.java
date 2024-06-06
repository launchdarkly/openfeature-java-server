package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import com.launchdarkly.sdk.server.subsystems.DataStoreTypes;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.exceptions.GeneralError;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedDataSource implements DataSource {
    private Duration startDelay;
    private boolean willError;
    private boolean initialized = false;
    private Object lock = new Object();
    DataSourceUpdateSink sink;

    DelayedDataSource(Duration delay, boolean error, DataSourceUpdateSink sink) {
        startDelay = delay;
        willError = error;
        this.sink = sink;
    }

    private static DataStoreTypes.SerializedItemDescriptor toSerialized(DataStoreTypes.DataKind kind, DataStoreTypes.ItemDescriptor item) {
        boolean isDeleted = item.getItem() == null;
        return new DataStoreTypes.SerializedItemDescriptor(item.getVersion(), isDeleted, kind.serialize(item));
    }

    public Future<Void> start() {
        var future = new CompletableFuture<Void>();
        var timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!willError) {
                    sink.updateStatus(DataSourceStatusProvider.State.VALID, null);
                    synchronized (lock) {
                        initialized = true;
                    }
                } else {
                    sink.updateStatus(DataSourceStatusProvider.State.OFF,
                        new DataSourceStatusProvider.ErrorInfo(
                            DataSourceStatusProvider.ErrorKind.NETWORK_ERROR,
                            404,
                            "bad",
                            LocalDateTime.now().toInstant(ZoneOffset.UTC)));
                }
                future.complete(null);
            }
        }, startDelay.toMillis());

        return future;
    }

    public boolean isInitialized() {
        synchronized (lock) {
            return initialized;
        }
    }

    public void close() throws IOException {
    }
}

class DelayedDataSourceFactory implements ComponentConfigurer<DataSource> {
    private Duration startDelay;
    private boolean willError;

    DelayedDataSourceFactory(Duration delay, boolean error) {
        startDelay = delay;
        willError = error;
    }

    @Override
    public DataSource build(ClientContext clientContext) {
        return new DelayedDataSource(startDelay, willError, clientContext.getDataSourceUpdateSink());
    }
}

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

    @Test
    public void itCanHandleClientThatIsNotInitializedImmediately() throws Exception {
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), false))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config);
        assertEquals(ProviderState.NOT_READY, provider.getState());

        var readyCount = new AtomicInteger();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_READY, (detail) -> {
            readyCount.getAndIncrement();
        });


        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        OpenFeatureAPI.getInstance().shutdown();

        assertEquals(ProviderState.READY, provider.getState());
        assertEquals(1, readyCount.get());
    }

    @Test
    public void itCanHandleClientThatIsNotInitializedImmediatelyAndErrors() throws Exception {
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), true))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config);
        assertEquals(ProviderState.NOT_READY, provider.getState());

        var errorCount = new AtomicInteger();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            errorCount.getAndIncrement();
        });


        GeneralError error = null;
        try {
            OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        } catch (GeneralError e) {
            error = e;
        }

        assertNotNull(error);

        assertEquals(ProviderState.ERROR, provider.getState());
        assertTrue(errorCount.get() >= 1);

        OpenFeatureAPI.getInstance().shutdown();
    }
}
