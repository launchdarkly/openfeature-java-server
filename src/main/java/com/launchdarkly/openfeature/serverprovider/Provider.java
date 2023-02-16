package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.LDClient;
import dev.openfeature.sdk.*;


public class Provider implements FeatureProvider {
    private class ProviderMetaData implements Metadata {
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

    public Provider(LDClient client) {
        this.client = client;
        logger = LDLogger.none(); // TODO: Implement config.

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
}
