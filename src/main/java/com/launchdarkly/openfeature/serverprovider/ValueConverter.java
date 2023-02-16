package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts an OpenFeature Value into an LDValue.
 */
class ValueConverter {
    private final LDLogger logger;

    public ValueConverter(LDLogger logger) {
        this.logger = logger;
    }

    public LDValue toLdValue(Value value) {
        if(value.isNull()) {
            return LDValue.ofNull();
        }
        if(value.isBoolean()) {
            return LDValue.of(value.asBoolean());
        }
        if(value.isNumber()) {
            return LDValue.of(value.asDouble());
        }
        if(value.isString()) {
            return LDValue.of(value.asString());
        }
        if(value.isInstant()) {
            DateTimeFormatter formatter = DateTimeFormatter
                    .ISO_DATE_TIME
                    .withZone(ZoneId.from(ZoneOffset.UTC));
            return LDValue.of(formatter.format(value.asInstant()));
        }
        if(value.isList()) {
            List<Value> asList = value.asList();
            List<LDValue> asLdValues = asList.stream()
                    .map(this::toLdValue)
                    .collect(Collectors.toList());
            return LDValue.arrayOf(asLdValues.toArray(new LDValue[0]));
        }
        if(value.isStructure()) {
            ObjectBuilder objectBuilder = LDValue.buildObject();
            Structure structure = value.asStructure();
            structure.asMap().forEach((itemKey, itemValue) -> {
                LDValue itemLdValue = toLdValue(itemValue);
                objectBuilder.put(itemKey, itemLdValue);
            });
            return objectBuilder.build();
        }

        // Could not convert, should not happen.
        logger.error("Could not convert Value in context to LDValue");
        return LDValue.ofNull();
    }
}
