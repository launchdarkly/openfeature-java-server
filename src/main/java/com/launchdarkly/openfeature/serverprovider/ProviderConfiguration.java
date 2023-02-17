package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.subsystems.ComponentConfigurer;
import com.launchdarkly.sdk.server.subsystems.LoggingConfiguration;

/**
 * An immutable configuration for the provider. Must be created using a {@link ProviderConfigurationBuilder}.
 * <pre><code>
 *     ProviderConfiguration.builder().build();
 * </code></pre>
 */
public final class ProviderConfiguration {
    ProviderConfigurationBuilder builder;

    private ProviderConfiguration(ProviderConfigurationBuilder builder) {
        this.builder = builder;
    }

    /**
     * A mutable object that uses the Builder pattern to specify properties for a {@link  ProviderConfiguration} object.
     */
    public static final class ProviderConfigurationBuilder {
        private ComponentConfigurer<LoggingConfiguration> loggingConfigurer;

        /**
         * Build a provider configuration.
         *
         * @return And immutable provider configuration.
         */
        public ProviderConfiguration build() {
            if (this.loggingConfigurer == null) {
                this.loggingConfigurer = Components.logging();
            }
            return new ProviderConfiguration(this);
        }

        /**
         * Assign an existing logging configuration.
         *
         * @param config The logging configuration to use.
         * @return This builder.
         */
        public ProviderConfigurationBuilder logging(ComponentConfigurer<LoggingConfiguration> config) {
            this.loggingConfigurer = config;
            return this;
        }

        /**
         * Create a logging configuration based on an {@link LDLogAdapter}.
         *
         * @param logAdapter The log adapter to use.
         * @return This builder.
         */
        public ProviderConfigurationBuilder logging(LDLogAdapter logAdapter) {
            this.loggingConfigurer = Components.logging(logAdapter);
            return this;
        }
    }

    /**
     * Get a new builder instance.
     *
     * @return A provider configuration builder.
     */
    public static ProviderConfigurationBuilder builder() {
        return new ProviderConfigurationBuilder();
    }

    /**
     * Get the logging factory to generate logging configuration.
     *
     * @return A logging factory.
     */
    public ComponentConfigurer<LoggingConfiguration> getLoggingConfigurationFactory() {
        return builder.loggingConfigurer;
    }
}
