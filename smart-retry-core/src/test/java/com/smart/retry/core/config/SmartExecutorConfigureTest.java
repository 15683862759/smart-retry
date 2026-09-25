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

    @Test
    void rejectsInvalidDeadTaskTimeout() {
        SmartExecutorConfigure.DeadTask deadTask = new SmartExecutorConfigure.DeadTask();

        Assertions.assertThrows(IllegalArgumentException.class, () -> deadTask.setTaskMaxExecuteTimeout(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> deadTask.setTaskMaxExecuteTimeout(-1));
    }

    @Test
    void acceptsPositiveDeadTaskTimeout() {
        SmartExecutorConfigure.DeadTask deadTask = new SmartExecutorConfigure.DeadTask();

        deadTask.setTaskMaxExecuteTimeout(1);

        Assertions.assertEquals(1, deadTask.getTaskMaxExecuteTimeout());
    }

    @Test
    void rejectsNullBooleanSwitches() {
        SmartExecutorConfigure.ClearTask clearTask = new SmartExecutorConfigure.ClearTask();
        SmartExecutorConfigure.DeadTask deadTask = new SmartExecutorConfigure.DeadTask();

        Assertions.assertThrows(IllegalArgumentException.class, () -> clearTask.setEnabled(null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> deadTask.setDeadTaskCheck(null));
    }

    @Test
    void acceptsBooleanSwitches() {
        SmartExecutorConfigure.ClearTask clearTask = new SmartExecutorConfigure.ClearTask();
        SmartExecutorConfigure.DeadTask deadTask = new SmartExecutorConfigure.DeadTask();

        clearTask.setEnabled(true);
        deadTask.setDeadTaskCheck(false);

        Assertions.assertTrue(clearTask.getEnabled());
        Assertions.assertFalse(deadTask.getDeadTaskCheck());
    }

    @Test
    void rejectsInvalidExecutorKeepAliveSeconds() {
        SmartExecutorConfigure.Executor executor = new SmartExecutorConfigure.Executor();

        Assertions.assertThrows(IllegalArgumentException.class, () -> executor.setKeepAliveSeconds(0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> executor.setKeepAliveSeconds(-1));
    }

    @Test
    void acceptsPositiveExecutorKeepAliveSeconds() {
        SmartExecutorConfigure.Executor executor = new SmartExecutorConfigure.Executor();

        executor.setKeepAliveSeconds(1);

        Assertions.assertEquals(1, executor.getKeepAliveSeconds());
    }
}
