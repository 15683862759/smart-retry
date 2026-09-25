package com.smart.retry.common;

import org.junit.Assert;
import org.junit.Test;

/**
 * 调度启动开关边界测试。
 *
 * @Author Codex
 * @Version SmartRetryRunFlagTest.java, v 0.1 2026年09月25日 Codex
 * @Description: 验证调度开关不接受空值，避免后续调度循环拆箱时抛出空指针。
 */
public class SmartRetryRunFlagTest {

    @Test
    public void setFlagRejectsNull() {
        SmartRetryRunFlag.setFlag(true);
        try {
            SmartRetryRunFlag.setFlag(null);
            Assert.fail("调度开关不应接受 null");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(SmartRetryRunFlag.getFlag());
        } finally {
            SmartRetryRunFlag.setFlag(false);
        }
    }
}
