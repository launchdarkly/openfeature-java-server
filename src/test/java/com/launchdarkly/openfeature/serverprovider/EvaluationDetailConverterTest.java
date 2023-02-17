package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.*;
import dev.openfeature.sdk.*;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class EvaluationDetailConverterTest {
    private final Double EPSILON = 0.00001;
    TestLogger testLogger = new TestLogger();
    EvaluationDetailConverter evaluationDetailConverter = new EvaluationDetailConverter(
            LDLogger.withAdapter(testLogger, "test-logger")
    );

    @Test
    public void itCanConvertDoubleEvaluationDetail() {
        EvaluationDetail<Double> inputDetail = EvaluationDetail.fromValue(
                3.0, 17, EvaluationReason.off());

        ProviderEvaluation<Double> converted = evaluationDetailConverter.toEvaluationDetails(inputDetail);

        assertEquals(3.0, converted.getValue(), EPSILON);
        assertEquals("17", converted.getVariant());
        assertEquals(Reason.DISABLED.toString(), converted.getReason());
    }

    @Test
    public void itCanConvertAStringEvaluationDetail() {
        EvaluationDetail<String> inputDetail = EvaluationDetail.fromValue(
                "the-value", 12, EvaluationReason.off());

        ProviderEvaluation<String> converted = evaluationDetailConverter.toEvaluationDetails(inputDetail);

        assertEquals("the-value", converted.getValue());
        assertEquals("12", converted.getVariant());
        assertEquals(Reason.DISABLED.toString(), converted.getReason());
    }

    @Test
    public void itCanHandleDifferentReasons() {
        EvaluationDetail<Boolean> off = EvaluationDetail.fromValue(true, 0, EvaluationReason.off());

        assertEquals(Reason.DISABLED.toString(), evaluationDetailConverter.toEvaluationDetails(off).getReason());

        EvaluationDetail<Boolean> targetMatch = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.targetMatch());

        assertEquals(Reason.TARGETING_MATCH.toString(), evaluationDetailConverter.toEvaluationDetails(targetMatch).getReason());

        EvaluationDetail<Boolean> fallthrough = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.fallthrough());

        assertEquals("FALLTHROUGH", evaluationDetailConverter.toEvaluationDetails(fallthrough).getReason());

        EvaluationDetail<Boolean> ruleMatch = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.ruleMatch(0, ""));

        assertEquals("RULE_MATCH", evaluationDetailConverter.toEvaluationDetails(ruleMatch).getReason());

        EvaluationDetail<Boolean> prereqFailed = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.prerequisiteFailed("the-key"));

        assertEquals("PREREQUISITE_FAILED", evaluationDetailConverter.toEvaluationDetails(prereqFailed).getReason());
    }

    @Test
    public void itCanHandleDifferentErrors() {
        EvaluationDetail<Boolean> clientNotReady = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.error(EvaluationReason.ErrorKind.CLIENT_NOT_READY));

        assertEquals(ErrorCode.PROVIDER_NOT_READY,
                evaluationDetailConverter.toEvaluationDetails(clientNotReady).getErrorCode());

        EvaluationDetail<Boolean> flagNotFound = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.error(EvaluationReason.ErrorKind.FLAG_NOT_FOUND));

        assertEquals(ErrorCode.FLAG_NOT_FOUND,
                evaluationDetailConverter.toEvaluationDetails(flagNotFound).getErrorCode());

        EvaluationDetail<Boolean> malformedFlag = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.error(EvaluationReason.ErrorKind.MALFORMED_FLAG));

        assertEquals(ErrorCode.PARSE_ERROR,
                evaluationDetailConverter.toEvaluationDetails(malformedFlag).getErrorCode());

        EvaluationDetail<Boolean> userNotSpecified = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.error(EvaluationReason.ErrorKind.USER_NOT_SPECIFIED));

        assertEquals(ErrorCode.TARGETING_KEY_MISSING,
                evaluationDetailConverter.toEvaluationDetails(userNotSpecified).getErrorCode());

        EvaluationDetail<Boolean> wrongType = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.error(EvaluationReason.ErrorKind.WRONG_TYPE));

        assertEquals(ErrorCode.TYPE_MISMATCH,
                evaluationDetailConverter.toEvaluationDetails(wrongType).getErrorCode());

        EvaluationDetail<Boolean> exception = EvaluationDetail.fromValue(
                true, 0, EvaluationReason.error(EvaluationReason.ErrorKind.EXCEPTION));

        assertEquals(ErrorCode.GENERAL,
                evaluationDetailConverter.toEvaluationDetails(exception).getErrorCode());
    }

    @Test
    public void itCanHandleAnArrayResult() {
        ArrayBuilder arrayBuilder = new ArrayBuilder();
        arrayBuilder.add(1.2);
        arrayBuilder.add("potato");
        arrayBuilder.add(new ArrayBuilder().add(17.0).build());
        arrayBuilder.add(new ObjectBuilder().put("aKey", "aValue").build());

        EvaluationDetail<LDValue> arrayDetail = EvaluationDetail.fromValue(
                arrayBuilder.build(), 0, EvaluationReason.off());

        ProviderEvaluation<Value> converted = evaluationDetailConverter.toEvaluationDetailsLdValue(arrayDetail);

        List<Value> convertedValue = converted.getValue().asList();

        assertEquals(1.2, convertedValue.get(0).asDouble(), EPSILON);
        assertEquals("potato", convertedValue.get(1).asString());

        List<Value> nested = convertedValue.get(2).asList();

        assertEquals(17.0, nested.get(0).asDouble(), EPSILON);

        Structure nestedStructure = convertedValue.get(3).asStructure();
        assertEquals("aValue", nestedStructure.getValue("aKey").asString());
    }

    @Test
    public void itCanHandleAnObjectResult() {
        ObjectBuilder objectBuilder = new ObjectBuilder();
        objectBuilder.put("aKey", "aValue");
        objectBuilder.put("objectKey", new ObjectBuilder().put("bKey", "bValue").build());
        objectBuilder.put("arrayKey", new ArrayBuilder().add(17.0).build());

        EvaluationDetail<LDValue> objectDetail = EvaluationDetail.fromValue(
                objectBuilder.build(), 0, EvaluationReason.off());

        ProviderEvaluation<Value> converted = evaluationDetailConverter.toEvaluationDetailsLdValue(objectDetail);

        Structure convertedStructure = converted.getValue().asStructure();

        assertEquals("aValue", convertedStructure.getValue("aKey").asString());

        Structure nestedStructure = convertedStructure.getValue("objectKey").asStructure();
        assertEquals("bValue", nestedStructure.getValue("bKey").asString());

        List<Value> nestedList = convertedStructure.getValue("arrayKey").asList();

        assertEquals(17.0, nestedList.get(0).asDouble(), EPSILON);
    }
}
