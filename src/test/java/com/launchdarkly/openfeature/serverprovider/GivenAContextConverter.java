package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Value;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

public class GivenAContextConverter {
    EvaluationContextConverter evaluationContextConverter = new EvaluationContextConverter(LDLogger.none());

    @Test public void itCanCreateAContextFromAKeyOnly() {
        LDContext expectedContext = LDContext.builder("the-key").build();
        LDContext converted = evaluationContextConverter.toLdContext(new ImmutableContext("the-key"));
        assertEquals(expectedContext, converted);

        HashMap<String, Value> attributes = new HashMap();
        attributes.put("key", new Value("the-key"));
        LDContext convertedKey = evaluationContextConverter.toLdContext(new ImmutableContext(attributes));
        assertEquals(expectedContext, convertedKey);
    }

    @Test public void itCanCreateAContextFromAKeyAndKind() {
        LDContext expectedContext = LDContext.builder(ContextKind.of("organization"), "org-key").build();

        HashMap<String, Value> attributes = new HashMap();
        attributes.put("kind", new Value("organization"));
        LDContext converted = evaluationContextConverter
                .toLdContext(new ImmutableContext("org-key", attributes));

        assertEquals(expectedContext, converted);
    }
}
