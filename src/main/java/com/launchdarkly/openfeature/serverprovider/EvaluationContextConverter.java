package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.ContextBuilder;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.ContextMultiBuilder;
import com.launchdarkly.sdk.LDContext;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts an OpenFeature EvaluationContext into a LDContext.
 */
class EvaluationContextConverter {
    private final LDLogger logger;
    private final ValueConverter valueConverter;

    public EvaluationContextConverter(LDLogger logger) {
        this.logger = logger;
        this.valueConverter = new ValueConverter(logger);
    }

    public LDContext toLdContext(EvaluationContext evaluationContext) {
        // Using the kind as a map here because getting a value from an immutable context that doesn't exist
        // throws. https://github.com/open-feature/java-sdk/pull/300
        Map<String, Value> attributes = evaluationContext.asMap();

        Value kindAsValue = attributes.get("kind");

        String finalKind = "user";
        if(kindAsValue != null && kindAsValue.isString()) {
            String kindString = kindAsValue.asString();
            if(kindString == "multi") {
                // A multi-context.
                return BuildMultiContext(evaluationContext);
            } else {
                // Single context with specified kind.
                finalKind = kindString;
            }
        } else if(kindAsValue != null) {
            logger.error("The evaluation context contained an invalid kind.");
        }
        // No kind specified, so it is a user kind.

        String targetingKey = evaluationContext.getTargetingKey();
        Value keyAsValue = attributes.get("key");

        targetingKey = getTargetingKey(targetingKey, keyAsValue);

        return BuildSingleContext(evaluationContext.asMap(), finalKind, targetingKey);
    }

    /**
     * Get and validate a targeting key.
     * @param targetingKey Targeting key as a string, or null.
     * @param keyAsValue Key as a Value, or null.
     * @return Returns a key, or null if one is not available.
     */
    private String getTargetingKey(String targetingKey, Value keyAsValue) {
        // Currently the targeting key will always have a value, but it can be empty.
        // So we want to tread an empty string as a not defined one. Later it could
        // become null, so we will need to check that.
        if(targetingKey != "" && keyAsValue != null && keyAsValue.isString()) {
            // There is both a targeting key and a key. It will work, but probably
            // is not intentional.
            logger.warn("EvaluationContext contained both a 'key' and 'targetingKey'.");
        }

        if(keyAsValue != null && !keyAsValue.isString()) {
            logger.warn("A non-string 'key' attribute was provided.");
        }

        // Targeting key takes precedence over key, because targeting key is in the spec.
        targetingKey = targetingKey != "" ? targetingKey : keyAsValue.asString();

        if(targetingKey == null) {
            logger.error("The EvaluationContext must contain either a 'targetingKey' or a 'key' and the type" +
                    "must be a string.");
        }
        return targetingKey;
    }

    private LDContext BuildMultiContext(EvaluationContext evaluationContext) {
        ContextMultiBuilder multiBuilder = LDContext.multiBuilder();

        evaluationContext.asMap().forEach((kind, attributes) -> {
            // Do not need to do anything for the kind key.
            if(kind == "kind") return;

            if(!attributes.isStructure()) {
                // The attributes need to be a structure to be part of a multi-context.
                logger.warn("Top level attributes in a multi-kind context should be Structure types.");
                return;
            }

            Map<String, Value> attributesMap = attributes.asStructure().asMap();
            Value keyAsValue = attributesMap.get("key");
            String targetingKey = attributesMap.get("targetingKey").toString();
            targetingKey = getTargetingKey(targetingKey, keyAsValue);

            LDContext singleContext = BuildSingleContext(attributesMap, kind, targetingKey);
            multiBuilder.add(singleContext);
        });
        return multiBuilder.build();
    }

    private LDContext BuildSingleContext(Map<String, Value> attributes, String kind, String key) {
        ContextBuilder builder = LDContext.builder(ContextKind.of(kind), key);

        attributes.forEach((attrKey, attrValue) -> {
            // Key has been processed, so we can skip it.
            if(attrKey == "key" || attrKey == "targetingKey") return;

            if(attrKey == "privateAttributes") {
                List<Value> valueList = attrValue.asList();
                if(valueList == null) {
                    logger.error("A key of 'privateAttributes' in an evaluation context must have a list value.");
                    return;
                }
                boolean allStrings = valueList.stream().allMatch(item -> item.isString());
                if(!allStrings) {
                    logger.error("A key of 'privateAttributes' must be a list of only string values.");
                    return;
                }

                builder.privateAttributes(
                        valueList.stream()
                                .map(item -> item.asString())
                                .collect(Collectors.toList())
                                .toArray(new String[0]));
                return;
            }
            if(attrKey == "anonymous") {
                // TODO: Anonymous stuff.
            }
            if(attrKey == "name") {
                // TODO: Name stuff.
            }

            builder.set(attrKey, valueConverter.toLdValue(attrValue));
        });

        return builder.build();
    }
}
