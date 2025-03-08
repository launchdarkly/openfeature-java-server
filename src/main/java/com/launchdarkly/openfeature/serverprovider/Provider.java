package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import dev.openfeature.sdk.*;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

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
        this(new LDClient(sdkKey, LDConfig.Builder.fromConfig(config)
            .wrapper(Components.wrapperInfo()
                .wrapperName("open-feature-java-server")
                .wrapperVersion(Version.SDK_VERSION)).build()));
    }

    Provider(LDClientInterface client) {
        this.client = client;
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

    @Override
    public ProviderState getState() {
        synchronized (state) {
            return state;
        }
    }

    @Override
    public void initialize(EvaluationContext evaluationContext) throws Exception {
        // If we are ready, then set the state. Don't return, because we still need to listen for future
        // changes.
        if (client.isInitialized()) {
            state = ProviderState.READY;
        }

        var completer = new CompletableFuture<Boolean>();

        client.getFlagTracker().addFlagChangeListener(detail -> {
            emitProviderConfigurationChanged(
                ProviderEventDetails.builder().flagsChanged(Collections.singletonList(detail.getKey())).build());
        });
        // Listen for future status changes.
        client.getDataSourceStatusProvider().addStatusListener((res) -> {
            handleDataSourceStatus(res, completer);
        });

        if(state == ProviderState.READY) {
            return;
        }

        handleDataSourceStatus(client.getDataSourceStatusProvider().getStatus(), completer);
        var successfullyInitialized = completer.get();

        if(!successfullyInitialized) {
            throw new RuntimeException("Failed to initialize LaunchDarkly client.");
        }
    }

    private void handleDataSourceStatus(DataSourceStatusProvider.Status res, CompletableFuture<Boolean> completer) {
        switch (res.getState()) {
            // We will not re-enter INITIALIZING, but it is here to make the switch exhaustive.
            case INITIALIZING: {
            }
            break;
            case INTERRUPTED: {
                setState(ProviderState.STALE);

                var message = res.getLastError() != null ? res.getLastError().getMessage() : "encountered an unknown error";
                emitProviderStale(ProviderEventDetails.builder().message(message).build());
            }
            break;
            case VALID: {
                boolean emit = false;
                synchronized (stateLock) {
                    // If we are ready, then we don't want to emit it again. Other conditions we may be updating the
                    // reason we are stale or interrupted, so we want to emit an event each time.
                    if (state != ProviderState.READY) {
                        emit = true;
                        setState(ProviderState.READY);
                    }
                }

                if (emit) {
                    completer.complete(true);
                    emitProviderReady(ProviderEventDetails.builder().build());
                }
            }
            break;
            case OFF: {
                // Currently there is not a shutdown state.
                // Our client/provider cannot be restarted, so we just go to error.
                setState(ProviderState.ERROR);
                completer.complete(false);
                emitProviderError(ProviderEventDetails.builder().message("Provider shutdown").build());
            }
        }
    }

    private void setState(ProviderState state) {
        synchronized (stateLock) {
            this.state = state;
        }
    }

    @Override
    public void shutdown() {
        try {
            client.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void track(String eventName, EvaluationContext ctx, TrackingEventDetails details) {
        if(ctx == null) {
            logger.info("Skipping this track call. LaunchDarkly SDK's track method requires a non-null EvaluationContext to work.");
            return;
        }

        if(details != null) {
            Double metricValue = null;
            if (details.getValue().isPresent()) {
                metricValue = (Double)details.getValue().get();
            }
            // Convert the Structure portion of the TrackingEventDetails into a key value map.
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
