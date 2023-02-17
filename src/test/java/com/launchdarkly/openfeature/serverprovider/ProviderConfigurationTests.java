package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogLevel;
import com.launchdarkly.logging.LDLogger;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class ProviderConfigurationTests {
    @Test
    public void itCanBuildADefaultConfiguration() {
        ProviderConfiguration defaultConfig = ProviderConfiguration.builder().build();
        assertNotNull(defaultConfig.getLoggingConfigurationFactory());
    }

    @Test
    public void itCanBeUsedWithALogAdapter() {
        TestLogger logAdapter = new TestLogger();
        ProviderConfiguration withLogAdapter = ProviderConfiguration.builder()
                .logging(logAdapter).build();

        LoggingConfiguration loggingConfig = withLogAdapter.getLoggingConfigurationFactory()
                .build(null);

        LDLogger logger = LDLogger.withAdapter(loggingConfig.getLogAdapter(), "the-name");
        logger.error("this is the error");

        assertTrue(logAdapter
                .getChannel("the-name")
                .expectedMessageInLevel(LDLogLevel.ERROR, "this is the error"));
    }

    @Test
    public void itCanBeUsedWithALoggingComponentConfigurer() {
        TestLogger logAdapter = new TestLogger();
        ProviderConfiguration withConfigurer = ProviderConfiguration.builder()
                .logging(Components.logging().adapter(logAdapter)).build();

        LoggingConfiguration loggingConfig = withConfigurer.getLoggingConfigurationFactory()
                .build(null);

        LDLogger logger = LDLogger.withAdapter(loggingConfig.getLogAdapter(), "the-name");
        logger.error("this is the error");

        assertTrue(logAdapter
                .getChannel("the-name")
                .expectedMessageInLevel(LDLogLevel.ERROR, "this is the error"));
    }
}
