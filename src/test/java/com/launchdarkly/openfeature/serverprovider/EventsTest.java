package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.integrations.TestData;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.ProviderEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests in this suite use a real client instance and the public constructor.
 * <p>
 * Detailed provider tests use a mock client to test specific result and context conversions.
 */
public class EventsTest {
    @Test
    public void emitsFlagChangeEvents() throws InterruptedException {
        var td = TestData.dataSource();
        td.update(td.flag("flagA").valueForAll(LDValue.of("test")));

        var provider = new Provider("fake-key", new LDConfig.Builder().dataSource(td)
            .events(Components.noEvents()).build());

        var changes = new ArrayBlockingQueue<String>(1);

        OpenFeatureAPI.getInstance().on(ProviderEvent.PROVIDER_CONFIGURATION_CHANGED, eventDetails -> {
            changes.add(eventDetails.getFlagsChanged().get(0));
        });
        OpenFeatureAPI.getInstance().setProviderAndWait(provider);
        td.update(td.flag("flagA").valueForAll(LDValue.of("updated")));

        var res = changes.take();
        assertEquals("flagA", res);

        td.update(td.flag("flagB").valueForAll(LDValue.of("new")));

        var res2 = changes.take();
        assertEquals("flagB", res2);
    }
}
