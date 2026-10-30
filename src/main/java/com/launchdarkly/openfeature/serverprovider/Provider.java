package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagChangeListener;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import dev.openfeature.sdk.*;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * An OpenFeature {@link FeatureProvider} which enables the use of the LaunchDarkly Server-Side SDK for Java
 * with OpenFeature.
 * <pre><code>
 * import dev.openfeature.sdk.OpenFeatureAPI;
 *
 * public class Main {
 *  public static void main(String[] args) {
 *    OpenFeatureAPI.getInstance().setProvider(new Provider("fake-key"));
 *
 *    // Refer to OpenFeature documentation for getting a client and performing evaluations.
 *  }
 * }
 * </code></pre>
 */
public class Provider extends EventProvider {
    private static final class ProviderMetaData implements Metadata {
        @Override
        public String getName() {
            return "LaunchDarkly.OpenFeature.ServerProvider";
        }
    }

    private final Metadata metaData = new ProviderMetaData();

    private final LDLogger logger;
    private final EvaluationDetailConverter evaluationDetailConverter;
    private final ValueConverter valueConverter;
    private final EvaluationContextConverter evaluationContextConverter;

    private final LDClientInterface client;

    private ProviderState state = ProviderState.NOT_READY;

    private final Object stateLock = new Object();

    private boolean initializing = false;

    private final boolean waitIndefinitely;

    // Completed when initialization reaches a terminal outcome: true when the data source becomes valid, false
    // when it reaches a permanent error or the provider is shut down. An indefinite initialization blocks on this.
    private final CompletableFuture<Boolean> initialized = new CompletableFuture<>();

    // Set when the provider is shut down, so a data source OFF caused by closing the client is recognized as an
    // expected teardown rather than reported as a provider error.
    private volatile boolean closed = false;

    // Registered in initialize() and removed in shutdown(). Held so they can be removed rather than leaked.
    private volatile FlagChangeListener flagChangeListener;
    private volatile DataSourceStatusProvider.StatusListener statusListener;

    // The largest start wait time that the SDK can represent in milliseconds. Anything longer is treated as an
    // indefinite wait rather than being passed to the SDK.
    private static final Duration MAX_FINITE_START_WAIT = Duration.ofMillis(Long.MAX_VALUE);

    /**
     * A start wait time which waits indefinitely, for use with
     * {@link Provider#Provider(String, LDConfig, Duration)}.
     * <p>
     * Java has no built-in infinite {@link Duration}, so this is the largest value {@link Duration} can hold. Any
     * start wait time too large to represent in milliseconds is treated the same way, so this constant and any
     * other "forever" duration both request an indefinite wait.
     */
    public static final Duration INFINITE_START_WAIT = ChronoUnit.FOREVER.getDuration();

    /**
     * Create a provider with the specified SDK and default configuration.
     * <p>
     * If you need to specify any configuration use {@link Provider#Provider(String, LDConfig)} instead.
     *
     * @param sdkKey the SDK key for your LaunchDarkly environment
     */
    public Provider(String sdkKey) {
        this(sdkKey, new LDConfig.Builder().build());
    }

    /**
     * Crate a provider with the specified SDK key and configuration.
     *
     * @param sdkKey the SDK key for your LaunchDarkly environment
     * @param config a client configuration object
     */
    public Provider(String sdkKey, LDConfig config) {
        this(makeClient(sdkKey, LDConfig.Builder.fromConfig(config)), false);
    }

    /**
     * Create a provider with the specified SDK key, configuration and start wait time.
     * <p>
     * The start wait time replaces the one configured with {@link LDConfig.Builder#startWait(Duration)} and bounds
     * the whole of initialization: the LaunchDarkly client waits for up to that long while it is constructed, and
     * {@link Provider#initialize(EvaluationContext)} then reports whether it became ready. {@link Duration#ZERO}
     * waits nowhere, so initialization fails unless the client is already ready and the application learns when it
     * becomes usable from provider events. {@link Provider#INFINITE_START_WAIT} waits indefinitely: nothing is
     * waited for during construction, and initialization does not complete until the data source becomes valid or
     * the provider is shut down.
     * <p>
     * {@link Provider#INFINITE_START_WAIT} must be passed here; it cannot be set through
     * {@link LDConfig.Builder#startWait(Duration)}, which accepts only durations the SDK can represent in
     * milliseconds. A negative start wait time behaves the same as {@link Duration#ZERO}, and a null start wait
     * time uses the SDK's default start wait.
     * <p>
     * An indefinite wait registered with {@code setProviderAndWait} blocks until the data source becomes valid,
     * and holds an OpenFeature API lock for the duration, so {@code OpenFeatureAPI.shutdown()} cannot end it. To
     * cancel such a wait, call {@link #shutdown()} on the provider directly from another thread, or register with
     * the non-blocking {@code setProvider} instead.
     *
     * @param sdkKey the SDK key for your LaunchDarkly environment
     * @param config a client configuration object
     * @param startWait how long to wait for the client to become ready, or {@link Provider#INFINITE_START_WAIT} to
     *                  wait indefinitely; null uses the SDK's default start wait
     */
    public Provider(String sdkKey, LDConfig config, Duration startWait) {
        this(sdkKey, config, startWait, isIndefinite(startWait));
    }

    private Provider(String sdkKey, LDConfig config, Duration startWait, boolean waitIndefinitely) {
        this(makeClient(sdkKey, LDConfig.Builder.fromConfig(config)
            .startWait(waitIndefinitely ? Duration.ZERO : startWait)), waitIndefinitely);
    }

    /**
     * Report whether a start wait time requests an indefinite wait.
     * <p>
     * A duration too large to represent in milliseconds, including {@link #INFINITE_START_WAIT}, is treated as
     * indefinite. This both honors a caller who spelled "forever" with some other large duration and avoids the
     * overflow the SDK would throw when it converts such a value to milliseconds.
     * <p>
     * A null start wait time is not indefinite: it falls through to the SDK, which applies its default start wait.
     */
    // Package-private for direct unit testing of the classification boundary.
    static boolean isIndefinite(Duration startWait) {
        return startWait != null && isLongerThan(startWait, MAX_FINITE_START_WAIT);
    }

    /**
     * Whether the first duration is strictly longer than the second.
     */
    private static boolean isLongerThan(Duration duration, Duration other) {
        return duration.compareTo(other) > 0;
    }

    private static LDClient makeClient(String sdkKey, LDConfig.Builder builder) {
        return new LDClient(sdkKey, builder
            .wrapper(Components.wrapperInfo()
                .wrapperName("open-feature-java-server")
                .wrapperVersion(Version.SDK_VERSION)).build());
    }

    Provider(LDClientInterface client) {
        this(client, true);
    }

    Provider(LDClientInterface client, boolean waitIndefinitely) {
        this.client = client;
        this.waitIndefinitely = waitIndefinitely;
        logger = client.getLogger();
        evaluationContextConverter = new EvaluationContextConverter(logger);
        evaluationDetailConverter = new EvaluationDetailConverter(logger);
        valueConverter = new ValueConverter(logger);
    }

    @Override
    public Metadata getMetadata() {
        return metaData;
    }

    @Override
    public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
        EvaluationDetail<Boolean> detail
            = this.client.boolVariationDetail(key, evaluationContextConverter.toLdContext(ctx), defaultValue);

        return evaluationDetailConverter.toEvaluationDetails(detail);
    }

    @Override
    public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
        EvaluationDetail<String> detail
            = this.client.stringVariationDetail(key, evaluationContextConverter.toLdContext(ctx), defaultValue);

        return evaluationDetailConverter.toEvaluationDetails(detail);
    }

    @Override
    public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
        EvaluationDetail<Integer> detail
            = this.client.intVariationDetail(key, evaluationContextConverter.toLdContext(ctx), defaultValue);

        return evaluationDetailConverter.toEvaluationDetails(detail);
    }

    @Override
    public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
        EvaluationDetail<Double> detail
            = this.client.doubleVariationDetail(key, evaluationContextConverter.toLdContext(ctx), defaultValue);

        return evaluationDetailConverter.toEvaluationDetails(detail);
    }

    @Override
    public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
        EvaluationDetail<LDValue> detail
            = this.client.jsonValueVariationDetail(
            key, evaluationContextConverter.toLdContext(ctx), valueConverter.toLdValue(defaultValue));

        return evaluationDetailConverter.toEvaluationDetailsLdValue(detail);
    }

    /**
     * Get the state of this provider.
     *
     * @deprecated The OpenFeature SDK tracks provider state itself, based on initialization and the events this
     * provider emits. Use {@link Client#getProviderState()} instead.
     * @return the state of this provider
     */
    @Deprecated
    @Override
    public ProviderState getState() {
        synchronized (stateLock) {
            return state;
        }
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        setInitializing(true);
        boolean successfullyInitialized = false;
        try {
            // The client's "initialized" latch stays set once it has received data, even while the data source is
            // interrupted; cached data can still be served, so that is reported READY. A data source which is OFF
            // is excluded: the client cannot recover from it, so it must not be reported as ready.
            DataSourceStatusProvider.Status currentStatus = client.getDataSourceStatusProvider().getStatus();
            if (client.isInitialized() && currentStatus.getState() != DataSourceStatusProvider.State.OFF) {
                setState(ProviderState.READY);
            }

            // Register listeners before evaluating the current status, so a change in between is not missed.
            flagChangeListener = detail -> emitProviderConfigurationChanged(
                ProviderEventDetails.builder().flagsChanged(Collections.singletonList(detail.getKey())).build());
            client.getFlagTracker().addFlagChangeListener(flagChangeListener);
            statusListener = this::handleDataSourceStatus;
            client.getDataSourceStatusProvider().addStatusListener(statusListener);

            if (getState() == ProviderState.READY) {
                successfullyInitialized = true;
            } else {
                handleDataSourceStatus(client.getDataSourceStatusProvider().getStatus());
                // Recording the failure with the state, under the lock, keeps a data source which becomes valid at
                // the same moment from being reported as a failed initialization, or from losing its ready event.
                synchronized (stateLock) {
                    if (!waitIndefinitely && !initialized.isDone()) {
                        completeState(ProviderState.ERROR);
                        initialized.complete(false);
                    }
                }
                // Blocks only on the indefinite path; otherwise the outcome was just recorded. The wait ends when
                // the data source becomes valid or permanently fails, or when the provider is shut down.
                successfullyInitialized = initialized.get();
            }
        } catch (InterruptedException e) {
            // CompletableFuture.get() clears the interrupt status when it throws; restore it so cooperative
            // cancellation still works for the caller.
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            setInitializing(false);
        }

        if (!successfullyInitialized) {
            // The state is already recorded, and the OpenFeature SDK emits its own error event when initialization
            // fails, so no event of our own is emitted here. The cause is attached so the SDK's event carries it.
            throw new RuntimeException(initializationFailureMessage());
        }
    }

    private String initializationFailureMessage() {
        if (closed) {
            return "The provider was shut down before the LaunchDarkly client initialized.";
        }
        DataSourceStatusProvider.ErrorInfo lastError = client.getDataSourceStatusProvider().getStatus().getLastError();
        return lastError != null
            ? "Failed to initialize LaunchDarkly client: " + lastError
            : "Failed to initialize LaunchDarkly client.";
    }

    private void handleDataSourceStatus(DataSourceStatusProvider.Status res) {
        switch (res.getState()) {
            // We will not re-enter INITIALIZING, but it is here to make the switch exhaustive.
            case INITIALIZING:
                break;
            case INTERRUPTED: {
                // Use toString() rather than getMessage(): an HTTP error carries its detail in toString() and has
                // a null message, matching the OFF branch below.
                var message = res.getLastError() != null
                    ? res.getLastError().toString()
                    : "encountered an unknown error";
                if (updateState(ProviderState.STALE)) {
                    emitProviderStale(ProviderEventDetails.builder().message(message).build());
                }
            }
            break;
            case VALID: {
                boolean emit;
                synchronized (stateLock) {
                    emit = completeState(ProviderState.READY);
                    initialized.complete(true);
                }
                if (emit) {
                    emitProviderReady(ProviderEventDetails.builder().build());
                }
            }
            break;
            case OFF: {
                // There is no shutdown state; the client/provider cannot be restarted, so this becomes an error.
                boolean emit;
                synchronized (stateLock) {
                    emit = completeState(ProviderState.ERROR);
                    initialized.complete(false);
                }
                var message = res.getLastError() != null
                    ? res.getLastError().toString()
                    : "the provider has encountered a permanent error or has been shutdown";
                // Closing the client reports OFF; that is an expected teardown, not a provider error, so suppress it.
                if (emit && !closed) {
                    emitProviderError(
                        ProviderEventDetails.builder().message(message).errorCode(ErrorCode.GENERAL).build());
                }
            }
            break;
            default:
                break;
        }
    }

    /**
     * Record the provider state and report whether the change should be emitted.
     * <p>
     * A state which does not change is not emitted; several data source statuses can map to the same provider
     * state, and a repeated state is not a change an application can act on. The state which completes
     * initialization is also not emitted here, because the OpenFeature SDK emits its own event for it.
     */
    private boolean updateState(ProviderState newState) {
        synchronized (stateLock) {
            if (state == newState) {
                return false;
            }
            state = newState;
            return !initializing;
        }
    }

    /**
     * Record the provider state which completes initialization and report whether the change should be emitted.
     * <p>
     * The OpenFeature SDK emits its own event for the transition which completes initialization, so that one is not
     * emitted here, but any state change after it is.
     */
    private boolean completeState(ProviderState newState) {
        synchronized (stateLock) {
            var changed = state != newState;
            var wasInitializing = initializing;
            state = newState;
            initializing = false;
            return changed && !wasInitializing;
        }
    }

    private void setInitializing(boolean initializing) {
        synchronized (stateLock) {
            this.initializing = initializing;
        }
    }

    private void setState(ProviderState state) {
        synchronized (stateLock) {
            this.state = state;
        }
    }

    @Override
    public void shutdown() {
        closed = true;
        // Unblock an indefinite initialization that is still waiting. Under the retry-conformant SDK the data
        // source never reports OFF for a transport failure, so shutting down is, besides the data source becoming
        // valid, the only thing that ends the wait.
        initialized.complete(false);
        removeListeners();
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            // Terminate the event emitter executor that EventProvider starts; otherwise it is leaked.
            super.shutdown();
        }
    }

    private void removeListeners() {
        var currentStatusListener = statusListener;
        if (currentStatusListener != null) {
            client.getDataSourceStatusProvider().removeStatusListener(currentStatusListener);
        }
        var currentFlagChangeListener = flagChangeListener;
        if (currentFlagChangeListener != null) {
            client.getFlagTracker().removeFlagChangeListener(currentFlagChangeListener);
        }
    }

    @Override
    public void track(String eventName, EvaluationContext ctx, TrackingEventDetails details) {
        if (ctx == null) {
            logger.info(
                    "The 'track' method was called with a null EvaluationContext. " +
                            "No 'track' event will be sent to LaunchDarkly. " +
                            "The LaunchDarkly SDK requires a non-null EvaluationContext.");
            return;
        }

        if (details != null) {
            Double metricValue = null;
            if (details.getValue().isPresent()) {
                metricValue = details.getValue().get().doubleValue();
            }
            // Convert the Structure portion of the TrackingEventDetails into a key value
            // map.
            // This will not put the metricValue extracted above into the map.
            LDValue data = valueConverter.toLdValue(new Value(details));

            if (metricValue != null) {
                client.trackMetric(eventName, evaluationContextConverter.toLdContext(ctx), data, metricValue);
            } else if (!data.isNull() && data.size() > 0) {
                client.trackData(eventName, evaluationContextConverter.toLdContext(ctx), data);
            } else {
                client.track(eventName, evaluationContextConverter.toLdContext(ctx));
            }
        } else {
            client.track(eventName, evaluationContextConverter.toLdContext(ctx));
        }
    }

    /**
     * Get the LaunchDarkly client associated with this provider.
     * <p>
     * This can be used to access LaunchDarkly features which are not available in OpenFeature.
     *
     * @return the launchdarkly client instance
     */
    public LDClientInterface getLdClient() {
        return client;
    }
}
