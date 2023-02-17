package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.Assert.*;

public class ContextConverterTest {
    TestLogger testLogger = new TestLogger();
    EvaluationContextConverter evaluationContextConverter = new EvaluationContextConverter(
            LDLogger.withAdapter(testLogger, "test-logger")
    );

    private TestLogger.TestChannel logs() {
        return testLogger.getChannel("test-logger");
    }

    @Test
    public void itCanCreateAContextFromAKeyOnly() {
        LDContext expectedContext = LDContext.builder("the-key").build();
        LDContext converted = evaluationContextConverter.toLdContext(new ImmutableContext("the-key"));
        assertEquals(expectedContext, converted);

        HashMap<String, Value> attributes = new HashMap<>();
        attributes.put("key", new Value("the-key"));
        LDContext convertedKey = evaluationContextConverter.toLdContext(new ImmutableContext(attributes));
        assertEquals(expectedContext, convertedKey);

        assertFalse(logs().containsAnyLogs());
    }

    @Test
    public void itCanCreateAContextFromAKeyAndKind() {
        LDContext expectedContext = LDContext.builder(ContextKind.of("organization"), "org-key").build();

        LDContext converted = evaluationContextConverter
                .toLdContext(new ImmutableContext("org-key", new HashMap<String, Value>() {{
                    put("kind", new Value("organization"));
                }}));

        assertEquals(expectedContext, converted);

        assertFalse(logs().containsAnyLogs());
    }

    @Test
    public void itLogsAnErrorWhenThereIsNoTargetingKey() {
        evaluationContextConverter.toLdContext(new ImmutableContext());

        assertTrue(logs().expectedMessageInLevel(LDLogLevel.ERROR,
                "The EvaluationContext must contain either a 'targetingKey' or a 'key' and the type " +
                        "must be a string."));
    }

    @Test
    public void itGivesTargetingKeyPrecedence() {
        LDContext expectedContext = LDContext.builder("key-to-use").build();

        HashMap<String, Value> attributes = new HashMap<>();
        attributes.put("key", new Value("key-not-to-use"));

        LDContext converted = evaluationContextConverter.toLdContext(
                new ImmutableContext("key-to-use", attributes));

        assertEquals(expectedContext, converted);

        assertTrue(logs().expectedMessageInLevel(LDLogLevel.WARN,
                "EvaluationContext contained both a 'key' and 'targetingKey'."));
    }

    @Test
    public void itHandlesAKeyOfIncorrectType() {
        HashMap<String, Value> attributes = new HashMap<>();
        attributes.put("key", new Value(0));

        evaluationContextConverter.toLdContext(
                new ImmutableContext(attributes));

        assertTrue(logs().expectedMessageInLevel(LDLogLevel.WARN,
                "A non-string 'key' attribute was provided."));

        assertTrue(logs().expectedMessageInLevel(LDLogLevel.ERROR,
                "The EvaluationContext must contain either a 'targetingKey' or a 'key' and the type " +
                        "must be a string."));
    }

    @Test
    public void itHandlesInvalidBuiltInAttributes() {
        LDContext expectedContext = LDContext.builder("user-key").build();

        HashMap<String, Value> attributes = new HashMap<>();
        attributes.put("name", new Value(3));
        attributes.put("anonymous", new Value("potato"));
        // The attributes were not valid, so they should be discarded.
        LDContext converted = evaluationContextConverter
                .toLdContext(new ImmutableContext("user-key", attributes));

        assertEquals(expectedContext, converted);

        assertTrue(logs().containsAnyLogs());
        assertTrue(logs().expectedMessageInLevel(LDLogLevel.ERROR,
                "The attribute 'name' must be a string and it was not."));
        assertTrue(logs().expectedMessageInLevel(LDLogLevel.ERROR,
                "The attribute 'anonymous' must be a boolean and it was not."));
        assertEquals(2, logs().countForLevel(LDLogLevel.ERROR));

    }

    @Test
    public void itHandlesValidBuiltInAttributes() {
        LDContext expectedContext = LDContext.builder("user-key")
                .name("the-name")
                .anonymous(true)
                .build();

        HashMap<String, Value> attributes = new HashMap<>();
        attributes.put("name", new Value("the-name"));
        attributes.put("anonymous", new Value(true));

        LDContext converted = evaluationContextConverter
                .toLdContext(new ImmutableContext("user-key", attributes));

        assertEquals(expectedContext, converted);
        assertFalse(logs().containsAnyLogs());
    }

    @Test
    public void itCanCreateAValidMultiKindContext() {
        LDContext expectedContext = LDContext.createMulti(
                LDContext.builder(ContextKind.of("organization"), "my-org-key")
                        .name("the-org-name")
                        .set("myCustomAttribute", "myAttributeValue")
                        .privateAttributes(new String[]{"myCustomAttribute"})
                        .build(),
                LDContext.builder("my-user-key")
                        .anonymous(true)
                        .set("myCustomAttribute", "myAttributeValue")
                        .build()
        );

        EvaluationContext evaluationContext = new ImmutableContext(new HashMap<String, Value>() {{
            put("kind", new Value("multi"));
            put("organization", new Value(new ImmutableStructure(new HashMap<String, Value>() {{
                put("name", new Value("the-org-name"));
                put("targetingKey", new Value("my-org-key"));
                put("myCustomAttribute", new Value("myAttributeValue"));
                put("privateAttributes", new Value(new ArrayList<Value>() {{
                    add(new Value("myCustomAttribute"));
                }}));
            }})));
            put("user", new Value(new ImmutableStructure(new HashMap<String, Value>() {{
                put("key", new Value("my-user-key"));
                put("anonymous", new Value(true));
                put("myCustomAttribute", new Value("myAttributeValue"));
            }})));
        }});

        assertEquals(expectedContext, evaluationContextConverter.toLdContext(evaluationContext));
        assertFalse(logs().containsAnyLogs());
    }
}
