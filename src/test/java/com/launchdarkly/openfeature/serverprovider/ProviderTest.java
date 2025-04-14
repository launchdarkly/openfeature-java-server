package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;
import dev.openfeature.sdk.*;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

import org.awaitility.Awaitility;

import static org.junit.jupiter.api.Assertions.*;

public class ProviderTest {
    LDClientInterface mockedLdClient = mock(LDClientInterface.class);

    /**
     * This test uses the package private constructor, which means that it does not set
     * wrapper information.
     */
    Provider ldProvider = new Provider(mockedLdClient);

    @Test
    public void itCanProvideMetadata() {
        Metadata metaData = ldProvider.getMetadata();
        assertEquals("LaunchDarkly.OpenFeature.ServerProvider", metaData.getName());
    }

    @Test
    public void itCanDoABooleanEvaluation() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");

        when(mockedLdClient.boolVariationDetail("the-key", LDContext.create("user-key"), false))
                .thenReturn(EvaluationDetail.fromValue(true, 12, EvaluationReason.fallthrough()));

        OpenFeatureAPI.getInstance().setProvider(ldProvider);

        Awaitility.await().forever().until(() -> OpenFeatureAPI
                .getInstance()
                .getClient()
                .getBooleanValue("the-key", false, evaluationContext));

        assertTrue(OpenFeatureAPI
                .getInstance()
                .getClient()
                .getBooleanValue("the-key", false, evaluationContext));

        FlagEvaluationDetails<Boolean> detailed = OpenFeatureAPI
                .getInstance()
                .getClient()
                .getBooleanDetails("the-key", false, evaluationContext);

        assertEquals(true, detailed.getValue());
        assertEquals("12", detailed.getVariant());
        assertEquals("FALLTHROUGH", detailed.getReason());
    }

    @Test
    public void itCanDoAStringEvaluation() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");

        when(mockedLdClient.stringVariationDetail("the-key", LDContext.create("user-key"), "default"))
                .thenReturn(EvaluationDetail
                        .fromValue("evaluated", 17, EvaluationReason.off()));

        OpenFeatureAPI.getInstance().setProvider(ldProvider);

        Awaitility.await().forever().until(() -> OpenFeatureAPI
                .getInstance()
                .getClient()
                .getStringValue("the-key", "default", evaluationContext).equals("evaluated"));

        assertEquals("evaluated", OpenFeatureAPI
                .getInstance()
                .getClient()
                .getStringValue("the-key", "default", evaluationContext));

        FlagEvaluationDetails<String> detailed = OpenFeatureAPI
                .getInstance()
                .getClient()
                .getStringDetails("the-key", "default", evaluationContext);

        assertEquals("evaluated", detailed.getValue());
        assertEquals("17", detailed.getVariant());
        assertEquals("DISABLED", detailed.getReason());
    }

    @Test
    public void itCanDoADoubleEvaluation() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");

        when(mockedLdClient.doubleVariationDetail("the-key", LDContext.create("user-key"), 0.0))
                .thenReturn(EvaluationDetail.fromValue(1.0, 42, EvaluationReason.targetMatch()));

        OpenFeatureAPI.getInstance().setProvider(ldProvider);

        Awaitility.await().forever().until(() -> OpenFeatureAPI
                .getInstance()
                .getClient()
                .getDoubleValue("the-key", 0.0, evaluationContext) != 0.0);

        assertEquals(1.0, OpenFeatureAPI
                .getInstance()
                .getClient()
                .getDoubleValue("the-key", 0.0, evaluationContext), 0.00001);

        FlagEvaluationDetails<Double> detailed = OpenFeatureAPI
                .getInstance()
                .getClient()
                .getDoubleDetails("the-key", 0.0, evaluationContext);

        assertEquals(1.0, detailed.getValue(), 0.00001);
        assertEquals("42", detailed.getVariant());
        assertEquals("TARGETING_MATCH", detailed.getReason());
    }

    @Test
    public void itCanDoAValueEvaluation() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");

        EvaluationDetail<LDValue> evaluationDetail = EvaluationDetail
            .fromValue(LDValue.buildObject().put("aKey", "aValue").build(), 84, EvaluationReason.targetMatch());

        when(mockedLdClient.jsonValueVariationDetail("the-key", LDContext.create("user-key"), LDValue.ofNull()))
            .thenReturn(evaluationDetail);

        OpenFeatureAPI.getInstance().setProvider(ldProvider);
        Value ofValue = OpenFeatureAPI
            .getInstance()
            .getClient()
            .getObjectValue("the-key", new Value(), evaluationContext);

        assertEquals("aValue", ofValue.asStructure().getValue("aKey").asString());

        FlagEvaluationDetails<Value> detailed = OpenFeatureAPI
            .getInstance()
            .getClient()
            .getObjectDetails("the-key", new Value(), evaluationContext);

        assertEquals("aValue", detailed.getValue().asStructure().getValue("aKey").asString());
        assertEquals("84", detailed.getVariant());
        assertEquals("TARGETING_MATCH", detailed.getReason());
    }

    @Test
    public void itCanTrackNoTrackingEventDetails() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");
        EvaluationContextConverter evaluationContextConverter = new EvaluationContextConverter(null);

        OpenFeatureAPI.getInstance().setProvider(ldProvider);

        OpenFeatureAPI
                .getInstance()
                .getClient().track("metric-key", evaluationContext);

        verify(mockedLdClient).track(
                "metric-key",
                evaluationContextConverter.toLdContext(evaluationContext));
    }

    @Test
    public void itCanTrackEmptyTrackingEventDetails() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");
        EvaluationContextConverter evaluationContextConverter = new EvaluationContextConverter(null);

        TrackingEventDetails trackingEventDetails = new MutableTrackingEventDetails(null);

        OpenFeatureAPI.getInstance().setProvider(ldProvider);

        OpenFeatureAPI
                .getInstance()
                .getClient().track("metric-key", evaluationContext, trackingEventDetails);

        verify(mockedLdClient).track(
                "metric-key",
                evaluationContextConverter.toLdContext(evaluationContext));
    }

    @Test
    public void itCanTrackTrackingEventDetailsWithNoValue() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");
        EvaluationContextConverter evaluationContextConverter = new EvaluationContextConverter(null);
        ValueConverter valueConverter = new ValueConverter(null);

        TrackingEventDetails trackingEventDetails = new MutableTrackingEventDetails(null).add("currency", "USD");

        OpenFeatureAPI.getInstance().setProvider(ldProvider);

        OpenFeatureAPI
                .getInstance()
                .getClient().track("metric-key", evaluationContext, trackingEventDetails);

        verify(mockedLdClient).trackData(
                "metric-key",
                evaluationContextConverter.toLdContext(evaluationContext),
                valueConverter.toLdValue(new Value(trackingEventDetails)));
    }

    @Test
    public void itCanTrackFullTrackingEventDetails() {
        EvaluationContext evaluationContext = new ImmutableContext("user-key");
        EvaluationContextConverter evaluationContextConverter = new EvaluationContextConverter(null);
        ValueConverter valueConverter = new ValueConverter(null);

        TrackingEventDetails trackingEventDetails = new MutableTrackingEventDetails(99.77).add("currency", "USD");

        OpenFeatureAPI.getInstance().setProvider(ldProvider);

        OpenFeatureAPI
                .getInstance()
                .getClient().track("metric-key", evaluationContext, trackingEventDetails);

        verify(mockedLdClient).trackMetric(
                "metric-key",
                evaluationContextConverter.toLdContext(evaluationContext),
                valueConverter.toLdValue(new Value(trackingEventDetails)),
                99.77);
    }
}
