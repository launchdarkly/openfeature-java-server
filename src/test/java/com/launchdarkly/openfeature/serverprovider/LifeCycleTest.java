package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.subsystems.ClientContext;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.DataSource;
import com.launchdarkly.sdk.server.subsystems.DataSourceUpdateSink;
import dev.openfeature.sdk.ErrorCode;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DelayedDataSource implements DataSource {
    private Duration startDelay;
    private boolean willError;
    private boolean errorAfterInitialization;
    private boolean useHttpError;
    private boolean initialized = false;
    private Object lock = new Object();
    private Timer timer;
    DataSourceUpdateSink sink;

    DelayedDataSource(Duration delay, boolean error, DataSourceUpdateSink sink) {
        this(delay, error, false, false, sink);
    }

    DelayedDataSource(Duration delay, boolean error, boolean errorAfterInitialization, DataSourceUpdateSink sink) {
        this(delay, error, errorAfterInitialization, false, sink);
    }

    DelayedDataSource(Duration delay, boolean error, boolean errorAfterInitialization, boolean useHttpError,
                      DataSourceUpdateSink sink) {
        startDelay = delay;
        willError = error;
        this.errorAfterInitialization = errorAfterInitialization;
        this.useHttpError = useHttpError;
        this.sink = sink;
    }

    public Future<Void> start() {
        var future = new CompletableFuture<Void>();
        timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!willError) {
                    sink.updateStatus(DataSourceStatusProvider.State.VALID, null);
                    synchronized (lock) {
                        initialized = true;
                    }
                    if (errorAfterInitialization) {
                        sink.updateStatus(DataSourceStatusProvider.State.OFF, errorInfo());
                    }
                } else {
                    sink.updateStatus(DataSourceStatusProvider.State.OFF, errorInfo());
                }
                future.complete(null);
            }
        }, startDelay.toMillis());

        return future;
    }

    private DataSourceStatusProvider.ErrorInfo errorInfo() {
        return useHttpError
            ? DataSourceStatusProvider.ErrorInfo.fromHttpError(401)
            : new DataSourceStatusProvider.ErrorInfo(
                DataSourceStatusProvider.ErrorKind.NETWORK_ERROR,
                404,
                "bad",
                LocalDateTime.now().toInstant(ZoneOffset.UTC));
    }

    public boolean isInitialized() {
        synchronized (lock) {
            return initialized;
        }
    }

    public void close() throws IOException {
        if (timer != null) {
            timer.cancel();
        }
    }
}

class ControllableDataSource implements DataSource {
    public Future<Void> start() {
        return new CompletableFuture<>();
    }

    public boolean isInitialized() {
        return false;
    }

    public void close() throws IOException {
    }
}

class ControllableDataSourceFactory implements ComponentConfigurer<DataSource> {
    final CompletableFuture<DataSourceUpdateSink> sink = new CompletableFuture<>();

    @Override
    public DataSource build(ClientContext clientContext) {
        sink.complete(clientContext.getDataSourceUpdateSink());
        return new ControllableDataSource();
    }
}

class DelayedDataSourceFactory implements ComponentConfigurer<DataSource> {
    private Duration startDelay;
    private boolean willError;
    private boolean errorAfterInitialization;
    private boolean useHttpError;

    DelayedDataSourceFactory(Duration delay, boolean error) {
        this(delay, error, false);
    }

    DelayedDataSourceFactory(Duration delay, boolean error, boolean errorAfterInitialization) {
        this(delay, error, errorAfterInitialization, false);
    }

    DelayedDataSourceFactory(Duration delay, boolean error, boolean errorAfterInitialization, boolean useHttpError) {
        startDelay = delay;
        willError = error;
        this.errorAfterInitialization = errorAfterInitialization;
        this.useHttpError = useHttpError;
    }

    @Override
    public DataSource build(ClientContext clientContext) {
        return new DelayedDataSource(
            startDelay,
            willError,
            errorAfterInitialization,
            useHttpError,
            clientContext.getDataSourceUpdateSink());
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
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        assertEquals(ProviderState.NOT_READY, provider.getState());

        var readyCount = new AtomicInteger();
        CompletableFuture<Boolean> gotReadyEvent = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_READY, (detail) -> {
            readyCount.getAndIncrement();
            gotReadyEvent.complete(true);
        });

        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        assertEquals(ProviderState.READY, provider.getState());
        assertTrue(gotReadyEvent.get(1000, TimeUnit.MILLISECONDS));

        Thread.sleep(100);
        assertEquals(1, readyCount.get());
    }

    @Test
    public void itCanHandleClientThatIsNotInitializedImmediatelyAndErrors() throws Exception {
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), true))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
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
    public void itEmitsReadyWhenTheDataSourceRecoversFromAFailedInitialization() throws Exception {
        var dataSourceFactory = new ControllableDataSourceFactory();
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(dataSourceFactory)
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        var sink = dataSourceFactory.sink.get(1000, TimeUnit.MILLISECONDS);

        var readyCount = new AtomicInteger();
        CompletableFuture<Boolean> gotReadyEvent = new CompletableFuture<>();
        CompletableFuture<Boolean> gotErrorEvent = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_READY, (detail) -> {
            readyCount.getAndIncrement();
            gotReadyEvent.complete(true);
        });

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            gotErrorEvent.complete(true);
        });

        sink.updateStatus(DataSourceStatusProvider.State.OFF, new DataSourceStatusProvider.ErrorInfo(
            DataSourceStatusProvider.ErrorKind.NETWORK_ERROR,
            404,
            "bad",
            LocalDateTime.now().toInstant(ZoneOffset.UTC)));

        GeneralError initializationError = null;
        try {
            OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        } catch (GeneralError e) {
            initializationError = e;
        }

        assertNotNull(initializationError);
        assertTrue(gotErrorEvent.get(1000, TimeUnit.MILLISECONDS));

        sink.updateStatus(DataSourceStatusProvider.State.VALID, null);

        assertTrue(gotReadyEvent.get(1000, TimeUnit.MILLISECONDS));

        Thread.sleep(100);
        assertEquals(1, readyCount.get());
    }

    @Test
    public void itIncludesTheDataSourceErrorInErrorEvents() throws Exception {
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), false, true))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        CompletableFuture<String> errorMessage = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            errorMessage.complete(detail.getMessage());
        });

        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        var message = errorMessage.get(1000, TimeUnit.MILLISECONDS);
        assertTrue(message.contains("404"));
        assertTrue(message.contains("bad"));
    }

    @Test
    public void itIncludesAnErrorCodeInErrorEvents() throws Exception {
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), false, true))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        CompletableFuture<ErrorCode> errorCode = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            errorCode.complete(detail.getErrorCode());
        });

        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        assertEquals(ErrorCode.GENERAL, errorCode.get(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void itIncludesHttpDataSourceErrorInErrorEvents() throws Exception {
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(100), false, true, true))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        CompletableFuture<String> errorMessage = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_ERROR, (detail) -> {
            errorMessage.complete(detail.getMessage());
        });

        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        var message = errorMessage.get(1000, TimeUnit.MILLISECONDS);
        assertNotNull(message);
        assertTrue(!message.isEmpty());
        assertTrue(message.contains("401"));
    }

    @Test
    public void itDoesNotWaitAgainWithAStartWaitTime() {
        var config = new LDConfig.Builder()
            .dataSource(new DelayedDataSourceFactory(Duration.ofSeconds(30), false))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Duration.ofMillis(50));

        var started = System.currentTimeMillis();
        assertThrows(GeneralError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(provider));

        assertTrue(System.currentTimeMillis() - started < 1000);
        assertEquals(ProviderState.ERROR, provider.getState());
    }

    @Test
    public void itDoesNotWaitWithAStartWaitTimeOfZero() {
        var config = new LDConfig.Builder()
            .dataSource(new DelayedDataSourceFactory(Duration.ofSeconds(30), false))
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Duration.ZERO);

        var started = System.currentTimeMillis();
        assertThrows(GeneralError.class, () -> OpenFeatureAPI.getInstance().setProviderAndWait(provider));

        assertTrue(System.currentTimeMillis() - started < 1000);
    }

    @Test
    public void itBecomesReadyWithinAPositiveStartWait() throws Exception {
        var config = new LDConfig.Builder()
            .dataSource(new DelayedDataSourceFactory(Duration.ofMillis(50), false))
            .events(Components.noEvents())
            .build();
        // A positive start wait longer than the data source takes to become valid: initialization succeeds.
        var provider = new Provider("fake-key", config, Duration.ofSeconds(5));

        var readyCount = new AtomicInteger();
        CompletableFuture<Boolean> gotReadyEvent = new CompletableFuture<>();
        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_READY, (detail) -> {
            readyCount.getAndIncrement();
            gotReadyEvent.complete(true);
        });

        var started = System.currentTimeMillis();
        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        assertEquals(ProviderState.READY, provider.getState());
        assertTrue(System.currentTimeMillis() - started < 5000);
        assertTrue(gotReadyEvent.get(1000, TimeUnit.MILLISECONDS));
        Thread.sleep(100);
        assertEquals(1, readyCount.get());
    }

    @Test
    public void itEmitsStaleThenReadyOnInterruptionAndRecovery() throws Exception {
        var dataSourceFactory = new ControllableDataSourceFactory();
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(dataSourceFactory)
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        var sink = dataSourceFactory.sink.get(1000, TimeUnit.MILLISECONDS);

        var readyCount = new AtomicInteger();
        var staleCount = new AtomicInteger();
        CompletableFuture<String> staleMessage = new CompletableFuture<>();
        CompletableFuture<Boolean> recoveredReadyEvent = new CompletableFuture<>();

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_READY, (detail) -> {
            if (readyCount.incrementAndGet() == 2) {
                recoveredReadyEvent.complete(true);
            }
        });
        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_STALE, (detail) -> {
            staleCount.incrementAndGet();
            staleMessage.complete(detail.getMessage());
        });

        // Complete initialization. The OpenFeature SDK emits the first ready event itself for the init outcome.
        sink.updateStatus(DataSourceStatusProvider.State.VALID, null);
        OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        assertEquals(ProviderState.READY, provider.getState());

        // An interruption is reported as stale, carrying the HTTP error detail.
        sink.updateStatus(DataSourceStatusProvider.State.INTERRUPTED,
            DataSourceStatusProvider.ErrorInfo.fromHttpError(503));
        var message = staleMessage.get(1000, TimeUnit.MILLISECONDS);
        assertTrue(message.contains("503"));

        // Recovery is reported as ready again.
        sink.updateStatus(DataSourceStatusProvider.State.VALID, null);
        assertTrue(recoveredReadyEvent.get(1000, TimeUnit.MILLISECONDS));

        Thread.sleep(100);
        assertEquals(2, readyCount.get());
        assertEquals(1, staleCount.get());
    }

    @Test
    public void itEmitsOnlyOneStaleEventForRepeatedInterruptions() throws Exception {
        var dataSourceFactory = new ControllableDataSourceFactory();
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(dataSourceFactory)
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        var sink = dataSourceFactory.sink.get(1000, TimeUnit.MILLISECONDS);

        var staleCount = new AtomicInteger();
        CompletableFuture<Boolean> firstStaleEvent = new CompletableFuture<>();
        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_STALE, (detail) -> {
            staleCount.incrementAndGet();
            firstStaleEvent.complete(true);
        });

        sink.updateStatus(DataSourceStatusProvider.State.VALID, null);
        OpenFeatureAPI.getInstance().setProviderAndWait(provider);

        // Repeated interruptions, each with a different error, map to the same STALE provider state and emit once.
        sink.updateStatus(DataSourceStatusProvider.State.INTERRUPTED,
            DataSourceStatusProvider.ErrorInfo.fromHttpError(503));
        assertTrue(firstStaleEvent.get(1000, TimeUnit.MILLISECONDS));

        sink.updateStatus(DataSourceStatusProvider.State.INTERRUPTED,
            DataSourceStatusProvider.ErrorInfo.fromHttpError(401));

        Thread.sleep(200);
        assertEquals(1, staleCount.get());
    }

    @Test
    public void aStartWaitTimeTooLargeToRepresentWaitsIndefinitely() {
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(new DelayedDataSourceFactory(Duration.ofSeconds(30), false))
            .events(Components.noEvents())
            .build();
        // Not Provider.INFINITE_START_WAIT, but another "forever" value. The range rule treats it as indefinite
        // rather than passing it to the SDK, where converting it to milliseconds would overflow.
        var provider = assertDoesNotThrow(() ->
            new Provider("fake-key", config, Duration.ofSeconds(Long.MAX_VALUE)));

        // Indefinite waiting does not block construction, so the provider is constructed promptly and not ready.
        assertEquals(ProviderState.NOT_READY, provider.getState());
        provider.shutdown();
    }

    @Test
    public void aNullStartWaitTimeUsesTheDefaultAndDoesNotThrow() {
        // A null start wait time is accepted rather than throwing; it falls through to the SDK's default.
        assertDoesNotThrow(() -> {
            var provider = new Provider("fake-key", new LDConfig.Builder().offline(true).build(), null);
            provider.initialize(new ImmutableContext("context-key"));
            assertEquals(ProviderState.READY, provider.getState());
        });
    }

    @Test
    public void shutdownEndsAnIndefiniteInitialization() throws Exception {
        var dataSourceFactory = new ControllableDataSourceFactory();
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(dataSourceFactory)
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        dataSourceFactory.sink.get(1000, TimeUnit.MILLISECONDS);

        // The data source never becomes valid, so an indefinite initialization would otherwise wait forever.
        CompletableFuture<Throwable> initOutcome = new CompletableFuture<>();
        var initThread = new Thread(() -> {
            try {
                provider.initialize(new ImmutableContext("context-key"));
                initOutcome.complete(null);
            } catch (Throwable t) {
                initOutcome.complete(t);
            }
        });
        initThread.start();

        // Let initialization park on the wait, then shut down to end it.
        Thread.sleep(200);
        provider.shutdown();

        var outcome = initOutcome.get(2000, TimeUnit.MILLISECONDS);
        assertNotNull(outcome);
        assertTrue(outcome instanceof RuntimeException);
    }

    @Test
    public void aDirectProviderShutdownCancelsAnIndefiniteSetProviderAndWait() throws Exception {
        // The documented escape for an indefinite setProviderAndWait that never becomes ready: a direct
        // provider.shutdown() from another thread (OpenFeatureAPI.shutdown() would deadlock on the API lock
        // setProviderAndWait holds).
        var dataSourceFactory = new ControllableDataSourceFactory();
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(dataSourceFactory)
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Provider.INFINITE_START_WAIT);
        dataSourceFactory.sink.get(1000, TimeUnit.MILLISECONDS);

        CompletableFuture<Throwable> setProviderOutcome = new CompletableFuture<>();
        var waiter = new Thread(() -> {
            try {
                OpenFeatureAPI.getInstance().setProviderAndWait(provider);
                setProviderOutcome.complete(null);
            } catch (Throwable t) {
                setProviderOutcome.complete(t);
            }
        });
        waiter.start();

        // The data source never becomes valid, so setProviderAndWait is parked indefinitely.
        Thread.sleep(300);
        assertFalse(setProviderOutcome.isDone());

        provider.shutdown();

        var outcome = setProviderOutcome.get(2000, TimeUnit.MILLISECONDS);
        assertNotNull(outcome);
    }

    @Test
    public void aFailedBoundedInitializationSurfacesTheDataSourceError() throws Exception {
        var dataSourceFactory = new ControllableDataSourceFactory();
        var config = new LDConfig.Builder()
            .startWait(Duration.ZERO)
            .dataSource(dataSourceFactory)
            .events(Components.noEvents())
            .build();
        var provider = new Provider("fake-key", config, Duration.ZERO);
        var sink = dataSourceFactory.sink.get(1000, TimeUnit.MILLISECONDS);

        // A bounded initialization fails; the thrown error must carry the data source's last error detail.
        sink.updateStatus(DataSourceStatusProvider.State.OFF,
            DataSourceStatusProvider.ErrorInfo.fromHttpError(401));

        var error = assertThrows(GeneralError.class,
            () -> OpenFeatureAPI.getInstance().setProviderAndWait(provider));
        assertTrue(error.getMessage().contains("401"));
    }

    @Test
    public void startWaitClassificationIsBoundaryCorrect() {
        // Indefinite: the sentinel, and any duration too large to represent in milliseconds.
        assertTrue(Provider.isIndefinite(Provider.INFINITE_START_WAIT));
        assertTrue(Provider.isIndefinite(Duration.ofSeconds(Long.MAX_VALUE)));
        assertTrue(Provider.isIndefinite(Duration.ofMillis(Long.MAX_VALUE).plusMillis(1)));
        // Bounded: exactly the largest representable millis, and smaller values.
        assertFalse(Provider.isIndefinite(Duration.ofMillis(Long.MAX_VALUE)));
        assertFalse(Provider.isIndefinite(Duration.ofSeconds(5)));
        assertFalse(Provider.isIndefinite(Duration.ZERO));
        assertFalse(Provider.isIndefinite(Duration.ofSeconds(-1)));
        // Null is not indefinite; it falls through to the SDK default.
        assertFalse(Provider.isIndefinite(null));
    }
}
