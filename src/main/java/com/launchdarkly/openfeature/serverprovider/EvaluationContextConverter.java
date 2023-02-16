package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.ContextBuilder;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts an OpenFeature EvaluationContext into a LDContext.
 */
class EvaluationContextConverter {
    private final LDLogger logger;

    EvaluationContextConverter(LDLogger logger) {
        this.logger = logger;
    }

    public LDContext toLdContext(EvaluationContext evaluationContext) {
        Value kindAsValue = evaluationContext.getValue("kind");

        String finalKind = "user";
        if(kindAsValue != null && kindAsValue.isString()) {
            String kindString = kindAsValue.asString();
            if(kindString == "multi") {
                // A multi-context.

            } else {
                // Single context with specified kind.
                finalKind = kindString;
            }
        } else if(kindAsValue != null) {
            logger.error("The evaluation context contained an invalid kind.");
        }
        // No kind specified, so it is a user kind.

        String targetingKey = evaluationContext.getTargetingKey();
        Value keyAsValue = evaluationContext.getValue("key");

        if(targetingKey != null && keyAsValue != null && keyAsValue.isString()) {
            // There is both a targeting key and a key. It will work, but probably
            // is not intentional.
            logger.warn("EvaluationContext contained both a 'key' and 'targetingKey'.");
        }

        // Targeting key takes precedence over key, because targeting key is in the spec.
        targetingKey = targetingKey != null ? targetingKey : keyAsValue.asString();

        return BuildSingleContext(evaluationContext.asMap(), finalKind, targetingKey);
    }

    private LDContext BuildSingleContext(Map<String, Value> attributes, String kind, String key) {
        ContextBuilder builder = LDContext.builder(ContextKind.of(kind), key);

        // TODO: Use the attributes.

        return builder.build();
    }
}
