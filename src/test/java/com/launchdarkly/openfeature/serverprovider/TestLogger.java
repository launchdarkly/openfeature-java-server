package com.launchdarkly.openfeature.serverprovider;

import com.launchdarkly.logging.LDLogAdapter;
import com.launchdarkly.logging.LDLogLevel;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A log adapter that can be used in tests to capture log messages
 * and validate the content of those messages.
 */
class TestLogger implements LDLogAdapter {
    private HashMap<String, TestChannel> channels = new HashMap<>();

    public TestChannel getChannel(String name) {
        return channels.get(name);
    }

    public class TestChannel implements Channel {
        private String name;

        private HashMap<LDLogLevel, ArrayList<String>> messages = new HashMap();

        public int countForLevel(LDLogLevel level) {
            if (messages.containsKey(level)) {
                return messages.get(level).size();
            }
            return 0;
        }

        public boolean expectedMessageInLevel(LDLogLevel level, String regexString) {
            if (messages.containsKey(level)) {
                return messages.get(level).stream().anyMatch(value -> {
                    return value.matches(regexString);
                });
            }
            return false;
        }

        public boolean containsAnyLogs() {
            return messages.size() != 0;
        }

        private TestChannel(String name) {
            this.name = name;
        }

        private void addMessage(LDLogLevel ldLogLevel, String message) {
            ArrayList<String> forLevel = messages.getOrDefault(ldLogLevel, new ArrayList());

            forLevel.add(message);

            // May already exist, but this makes the logic simpler.
            messages.put(ldLogLevel, forLevel);
        }

        @Override
        public boolean isEnabled(LDLogLevel ldLogLevel) {
            return true;
        }

        @Override
        public void log(LDLogLevel ldLogLevel, Object o) {
            addMessage(ldLogLevel, o.toString());
        }

        @Override
        public void log(LDLogLevel ldLogLevel, String s, Object o) {
            addMessage(ldLogLevel, String.format(s, o));
        }

        @Override
        public void log(LDLogLevel ldLogLevel, String s, Object o, Object o1) {
            addMessage(ldLogLevel, String.format(s, o, o1));
        }

        @Override
        public void log(LDLogLevel ldLogLevel, String s, Object... objects) {
            addMessage(ldLogLevel, String.format(s, objects));
        }
    }

    @Override
    public Channel newChannel(String name) {
        TestChannel newChannel = new TestChannel(name);
        channels.put(name, newChannel);
        return newChannel;
    }
}
