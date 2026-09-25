package com.smart.retry.core.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SmartExecutorConfigureTest {

    @Test
    void rejectsInvalidHealthIntervals() {
        SmartExecutorConfigure.Health health = new SmartExecutorConfigure.Health();

        Assertions.assertThrows(IllegalArgumentException.class, () -> health.setInterval(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> health.setScanInterval(-1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> health.setTimeout(0));
    }

    @Test
    void acceptsPositiveHealthIntervals() {
        SmartExecutorConfigure.Health health = new SmartExecutorConfigure.Health();

        health.setInterval(1);
        health.setTimeout(1);
        health.setScanInterval(1);

        Assertions.assertEquals(1, health.getInterval());
        Assertions.assertEquals(1, health.getTimeout());
        Assertions.assertEquals(1, health.getScanInterval());
    }
}
