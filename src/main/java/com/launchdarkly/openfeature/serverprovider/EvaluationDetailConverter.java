package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDValue;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;

/**
 * Converts an EvaluationDetail into an OpenFeature ResolutionDetails.
 */
class EvaluationDetailConverter {
    LDLogger logger;
    LDValueConverter ldValueConverter;

    public EvaluationDetailConverter(LDLogger logger) {
        this.logger = logger;
        this.ldValueConverter = new LDValueConverter(logger);
    }

    /**
     * This method can convert types other than Structures or Arrays.
     *
     * @param detail The detail to convert to a provider detail.
     * @param <T>    The type of the evaluation result.
     * @return The provider detail.
     */
    public <T> ProviderEvaluation<T> toEvaluationDetails(EvaluationDetail<T> detail) {
        T value = detail.getValue();
        EvaluationReason reason = detail.getReason();
        boolean isDefault = detail.isDefaultValue();
        int variationIndex = detail.getVariationIndex();

        return getProviderEvaluation(value, reason, isDefault, variationIndex);
    }

    /**
     * Convert Array and Structure type results.
     * There are two different methods there isn't specialization, so there will need to be runtime decision
     * about which method to call. This should be based on the method called in the provider interface.
     * @param detail The detail to convert.
     * @return The converted detail.
     */
    public ProviderEvaluation<Value> toEvaluationDetailsLdValue(EvaluationDetail<LDValue> detail) {
        Value value = ldValueConverter.toValue(detail.getValue());
        EvaluationReason reason = detail.getReason();
        boolean isDefault = detail.isDefaultValue();
        int variationIndex = detail.getVariationIndex();

        return getProviderEvaluation(value, reason, isDefault, variationIndex);
    }

    private static <T> ProviderEvaluation<T> getProviderEvaluation(T value, EvaluationReason reason, boolean isDefault, int variationIndex) {
        ProviderEvaluation.ProviderEvaluationBuilder<T> builder = ProviderEvaluation.<T>builder()
                .value(value)
                .reason(KindToString(reason.getKind()));
        if (reason.getKind() == EvaluationReason.Kind.ERROR) {
            builder.errorCode(ErrorKindToErrorCode(reason.getErrorKind()));
        }
        if (!isDefault) {
            builder.variant(String.valueOf(variationIndex));
        }

        return builder.build();
    }

    private static String KindToString(EvaluationReason.Kind kind) {
        switch (kind) {
            case OFF:
                return Reason.DISABLED.toString();
            case TARGET_MATCH:
                return Reason.TARGETING_MATCH.toString();
            case ERROR:
                return Reason.ERROR.toString();
            case FALLTHROUGH:
                // Intentional fallthrough
            case RULE_MATCH:
                // Intentional fallthrough
            case PREREQUISITE_FAILED:
                // Intentional fallthrough
            default:
                return kind.toString();
        }
    }

    private static ErrorCode ErrorKindToErrorCode(EvaluationReason.ErrorKind errorKind) {
        switch (errorKind) {
            case CLIENT_NOT_READY:
                return ErrorCode.PROVIDER_NOT_READY;
            case FLAG_NOT_FOUND:
                return ErrorCode.FLAG_NOT_FOUND;
            case MALFORMED_FLAG:
                return ErrorCode.PARSE_ERROR;
            case USER_NOT_SPECIFIED:
                return ErrorCode.TARGETING_KEY_MISSING;
            case WRONG_TYPE:
                return ErrorCode.TYPE_MISMATCH;
            case EXCEPTION:
                // Intentional fallthrough
            default:
                return ErrorCode.GENERAL;
        }
    }
}
