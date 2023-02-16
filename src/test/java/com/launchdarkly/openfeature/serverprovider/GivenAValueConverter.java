package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class GivenAValueConverter {
    private ValueConverter valueConverter = new ValueConverter(LDLogger.none());
    private final Double EPSILON = 0.00001;

    @Test
    public void itCanConvertNull() {
        LDValue value = valueConverter.toLdValue(new Value());
        assertTrue(value.isNull());
    }

    @Test
    public void itCanConvertBooleans() {
        LDValue trueValue = valueConverter.toLdValue(new Value(true));
        assertTrue(trueValue.booleanValue());
        assertEquals(trueValue.getType(), LDValueType.BOOLEAN);

        LDValue falseValue = valueConverter.toLdValue(new Value(false));
        assertFalse(falseValue.booleanValue());
        assertEquals(falseValue.getType(), LDValueType.BOOLEAN);
    }

    @Test
    public void itCanConvertNumbers() {
        LDValue zeroValue = valueConverter.toLdValue(new Value(0));
        assertEquals(0.0, zeroValue.doubleValue(), EPSILON);
        assertTrue(zeroValue.isNumber());

        LDValue numberValue = valueConverter.toLdValue(new Value(1000));
        assertEquals(numberValue.doubleValue(), 1000.0, EPSILON);
        assertTrue(numberValue.isNumber());
    }

    @Test
    public void itCanConvertStrings() {
        LDValue stringValue = valueConverter.toLdValue(new Value("the string"));
        assertTrue(stringValue.isString());
        assertEquals("the string", stringValue.stringValue());
    }

    @Test
    public void itCanConvertInstants() {
        LDValue dateString = valueConverter.toLdValue(new Value(Instant.ofEpochMilli(0)));
        assertEquals("1970-01-01T00:00:00Z", dateString.stringValue());
    }

    @Test
    public void itCanConvertLists() {
        Value ofValueList = new Value(new ArrayList<Value>() {{
            add(new Value(true));
            add(new Value(false));
            add(new Value(17));
            add(new Value(42.5));
            add(new Value("string"));
        }});

        LDValue ldValue = valueConverter.toLdValue(ofValueList);
        List<LDValue> ldValueList = new ArrayList();
        ldValue.values().forEach(ldValueList::add);

        assertEquals(5, ldValueList.size());

        assertTrue(ldValueList.get(0).booleanValue());
        assertFalse(ldValueList.get(1).booleanValue());
        assertEquals(17.0, ldValueList.get(2).doubleValue(), EPSILON);
        assertEquals(42.5, ldValueList.get(3).doubleValue(), EPSILON);
        assertEquals("string", ldValueList.get(4).stringValue());
    }

    @Test
    public void itCanConvertStructures() {
        Value ofValueStructure = new Value(new ImmutableStructure(new HashMap<String, Value>(){{
            put("aKey", new Value("aValue"));
            put("structKey", new Value(new ImmutableStructure(new HashMap<String, Value>(){{
                put("bKey", new Value("bValue"));
            }})));
        }}));

        LDValue ldValue = valueConverter.toLdValue(ofValueStructure);

        List<String> keyList = new ArrayList();
        ldValue.keys().forEach(keyList::add);

        List<LDValue> valueList = new ArrayList();
        ldValue.values().forEach(valueList::add);

        assertEquals("aKey", keyList.get(0));
        assertEquals("structKey", keyList.get(1));

        assertEquals("aValue", valueList.get(0).stringValue());

        assertEquals("bKey", valueList.get(1).keys().iterator().next());
        assertEquals("bValue", valueList.get(1).values().iterator().next().stringValue());
    }
}
