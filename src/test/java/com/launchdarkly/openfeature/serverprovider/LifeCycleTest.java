package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvent;
import dev.openfeature.sdk.ProviderState;
import dev.openfeature.sdk.exceptions.GeneralError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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

class NeverReadyDataSource implements DataSource {
    public Future<Void> start() {
        return new CompletableFuture<>();
    }

    public boolean isInitialized() {
        return false;
    }

    public void close() throws IOException {
    }
}

class NeverReadyDataSourceFactory implements ComponentConfigurer<DataSource> {
    @Override
    public DataSource build(ClientContext clientContext) {
        return new NeverReadyDataSource();
    }
}

/**
 * Tests in this suite use a real client instance and the public constructor.
 * <p>
 * Detailed provider tests use a mock client to test specific result and context conversions.
 */
public class LifeCycleTest {
    @AfterEach
    public void tearDown() {
        OpenFeatureAPI.getInstance().shutdown();
    }

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
        });
    }

    @Test
    public void twoArgumentConstructorPreservesConfigStartWait() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            var config = new LDConfig.Builder()
                .startWait(Duration.ZERO)
                .dataSource(new NeverReadyDataSourceFactory())
                .events(Components.noEvents())
                .build();
            var provider = new Provider("fake-key", config);
            provider.shutdown();
        });
    }

    @Test
    public void itEmitsReadyEvents() throws ExecutionException, InterruptedException, TimeoutException {
        var provider = new Provider("fake-key", new LDConfig.Builder()
            .offline(true).build());

        var readyCount = new AtomicInteger();
        var errorCount = new AtomicInteger();
        var staleCount = new AtomicInteger();
        CompletableFuture<Boolean> gotReadyEvent = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_READY, (detail) -> {
            readyCount.getAndIncrement();
            gotReadyEvent.complete(true);
        });

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_STALE, (detail) -> {
            staleCount.getAndIncrement();
        });

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            errorCount.getAndIncrement();
        });

        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        assertTrue(gotReadyEvent.get(1000, TimeUnit.MILLISECONDS));
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

        CompletableFuture<Boolean> gotErrorEvent = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            gotErrorEvent.complete(true);
        });

        GeneralError error = null;
        try {
            OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        } catch (GeneralError e) {
            error = e;
        }

        assertNotNull(error);

        assertEquals(ProviderState.ERROR, provider.getState());

        assertTrue(gotErrorEvent.get(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void initializationFailsWithoutWaitingAgainWhenStartWaitIsPositive() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            var config = new LDConfig.Builder()
                .dataSource(new NeverReadyDataSourceFactory())
                .events(Components.noEvents())
                .build();
            var provider = new Provider("fake-key", config, Duration.ofMillis(300));
            try {
                // The constructor consumed the start wait, so initialization must not wait a second time.
                assertTimeoutPreemptively(Duration.ofMillis(100), () -> {
                    var error = assertThrows(RuntimeException.class,
                        () -> provider.initialize(new ImmutableContext("context-key")));
                    assertTrue(error.getMessage()
                        .contains("The client did not initialize within the start wait duration."));
                });
                assertEquals(ProviderState.ERROR, provider.getState());
            } finally {
                provider.shutdown();
            }
        });
    }

    @Test
    public void initializationSucceedsWhenTheClientBecomesReadyDuringTheStartWait() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            var config = new LDConfig.Builder()
                .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), false))
                .events(Components.noEvents())
                .build();
            var provider = new Provider("fake-key", config, Duration.ofMillis(500));
            try {
                assertDoesNotThrow(() -> provider.initialize(new ImmutableContext("context-key")));
                assertEquals(ProviderState.READY, provider.getState());
            } finally {
                provider.shutdown();
            }
        });
    }

    @Test
    public void initializationReportsPermanentFailureAfterAPositiveStartWait() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            var config = new LDConfig.Builder()
                .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), true))
                .events(Components.noEvents())
                .build();
            var provider = new Provider("fake-key", config, Duration.ofMillis(500));
            try {
                var error = assertThrows(RuntimeException.class,
                    () -> provider.initialize(new ImmutableContext("context-key")));
                assertEquals("Failed to initialize LaunchDarkly client.", error.getMessage());
            } finally {
                provider.shutdown();
            }
        });
    }

    @Test
    public void initializationWaitsIndefinitelyWhenStartWaitIsZero() throws Exception {
        var config = new LDConfig.Builder()
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(200), false))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Duration.ZERO);

        assertDoesNotThrow(() -> provider.initialize(new ImmutableContext("context-key")));
        assertEquals(ProviderState.READY, provider.getState());
        provider.shutdown();
    }
}
