package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.ArrayBuilder;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;
import dev.openfeature.sdk.Structure;
import dev.openfeature.sdk.Value;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LdValueConverterTest {
    private final LDValueConverter valueConverter = new LDValueConverter(LDLogger.none());
    private final Double EPSILON = 0.00001;

    @Test
    public void itCanConvertNull() {
        Value value = valueConverter.toValue(LDValue.ofNull());
        assertTrue(value.isNull());
    }

    @Test
    public void itCanConvertBooleans() {
        Value trueValue = valueConverter.toValue(LDValue.of(true));
        assertTrue(trueValue.asBoolean());
        assertTrue(trueValue.isBoolean());

        Value falseValue = valueConverter.toValue(LDValue.of(false));
        assertFalse(falseValue.asBoolean());
        assertTrue(falseValue.isBoolean());
    }

    @Test
    public void itCanConvertNumbers() {
        Value zeroValue = valueConverter.toValue(LDValue.of(0.0));
        assertEquals(0.0, zeroValue.asDouble(), EPSILON);
        assertTrue(zeroValue.isNumber());

        Value numberValue = valueConverter.toValue(LDValue.of(1000.0));
        assertEquals(numberValue.asDouble(), 1000.0, EPSILON);
        assertTrue(numberValue.isNumber());
    }

    @Test
    public void itCanConvertStrings() {
        Value stringValue = valueConverter.toValue(LDValue.of("the string"));
        assertTrue(stringValue.isString());
        assertEquals("the string", stringValue.asString());
    }

    @Test
    public void itCanConvertLists() {
        LDValue ldValueList = new ArrayBuilder()
                .add(true)
                .add(false)
                .add(17.0)
                .add(42.5)
                .add("string").build();

        Value ofValue = valueConverter.toValue(ldValueList);
        List<Value> ofValueList = ofValue.asList();

        assertEquals(5, ofValueList.size());

        assertTrue(ofValueList.get(0).asBoolean());
        assertFalse(ofValueList.get(1).asBoolean());
        assertEquals(17.0, ofValueList.get(2).asDouble(), EPSILON);
        assertEquals(42.5, ofValueList.get(3).asDouble(), EPSILON);
        assertEquals("string", ofValueList.get(4).asString());
    }

    @Test
    public void itCanConvertStructures() {
        LDValue ldStructValue = new ObjectBuilder()
                .put("aKey", "aValue")
                .put("structKey", new ObjectBuilder().put("bKey", "bValue").build()).build();

        Value ofValue = valueConverter.toValue(ldStructValue);

        Structure ofStructure = ofValue.asStructure();
        assertEquals("aValue", ofStructure.getValue("aKey").asString());

        Structure nested = ofStructure.getValue("structKey").asStructure();
        assertEquals("bValue", nested.getValue("bKey").asString());
    }
}
