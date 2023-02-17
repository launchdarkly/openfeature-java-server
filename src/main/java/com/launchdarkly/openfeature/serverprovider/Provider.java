package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;
import dev.openfeature.sdk.*;

/**
 * An OpenFeature {@link FeatureProvider} which enables the use of the LaunchDarkly Server-Side SDK for Java
 * with OpenFeature.
 * <pre><code>
 *import dev.openfeature.sdk.OpenFeatureAPI;
 *import com.launchdarkly.sdk.server.LDClient;
 *
 *public class Main {
 *  public static void main(String[] args) {
 *    LDClient ldClient = new LDClient("my-sdk-key");
 *    OpenFeatureAPI.getInstance().setProvider(new Provider(ldClient));
 *
 *    // Refer to OpenFeature documentation for getting a client and performing evaluations.
 *  }
 *}
 * </code></pre>
 */
public class Provider implements FeatureProvider {
    private static final class ProviderMetaData implements Metadata {
        @Override
        public String getName() {
            return "LaunchDarkly.OpenFeature.ServerProvider";
        }
    }

    private final Metadata metaData = new ProviderMetaData();

    private LDLogger logger;
    private EvaluationDetailConverter evaluationDetailConverter;
    private ValueConverter valueConverter;
    private EvaluationContextConverter evaluationContextConverter;

    private LDClient client;

    /**
     * Create a provider with the given LaunchDarkly client and provider configuration.
     * <pre><code>
     * // Using the provider with a custom log level.
     * new Provider(ldclient, ProviderConfiguration
     *     .builder()
     *     .logging(Components.logging().level(LDLogLevel.INFO)
     *     .build());
     * </code></pre>
     *
     * @param client A {@link LDClient} instance.
     * @param config Configuration for the provider.
     */
    public Provider(LDClient client, ProviderConfiguration config) {
        this.client = client;
        LoggingConfiguration loggingConfig = config.getLoggingConfigurationFactory().build(null);
        LDLogAdapter adapter = loggingConfig.getLogAdapter();
        logger = LDLogger.withAdapter(adapter, loggingConfig.getBaseLoggerName());

        evaluationContextConverter = new EvaluationContextConverter(logger);
        evaluationDetailConverter = new EvaluationDetailConverter(logger);
        valueConverter = new ValueConverter(logger);
    }

    /**
     * Create a provider with the given LaunchDarkly client.
     * <p>
     * The provider will be created with default configuration.
     *
     * @param client A {@link LDClient} instance.
     */
    public Provider(LDClient client) {
        this(client, ProviderConfiguration.builder().build());
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
}
