package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.ArrayBuilder;
import com.launchdarkly.sdk.LDValue;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Provides methods for converting an LDValue into an OpenFeature Value.
 */
public class LDValueConverter {
    private final LDLogger logger;

    public LDValueConverter(LDLogger logger) {
        this.logger = logger;
    }

    public Value toValue(LDValue value) {
        switch(value.getType()) {
            case NULL:
                return new Value();
            case BOOLEAN:
                return new Value(value.booleanValue());
            case NUMBER:
                return new Value(value.doubleValue());
            case STRING:
                return new Value(value.stringValue());
            case ARRAY:
                return new Value(StreamSupport.stream(value.values().spliterator(), false)
                        .map(this::toValue)
                        .collect(Collectors.toList()));
            case OBJECT:
                List<String> keys = new ArrayList();
                value.keys().forEach(keys::add);

                List<LDValue> values = new ArrayList();
                value.values().forEach(values::add);

                if(keys.size() != values.size()) {
                    logger.error("Could not get Object representation from LDValue. Returning a new Value(null).");
                    return new Value();
                }

                HashMap<String, Value> converted = new HashMap();
                for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
                    String key = keys.get(keyIndex);
                    LDValue itemValue = values.get(keyIndex);
                    converted.put(key, toValue(itemValue));
                }
                return new Value(new ImmutableStructure(converted));
            default:
                logger.error("Unrecognized type converting result. Returning a new Value(null).");
                // Will only happen if new types are added.
                return new Value();
        }
    }
}
