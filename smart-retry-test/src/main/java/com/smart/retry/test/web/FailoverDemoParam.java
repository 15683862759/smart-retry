package com.smart.retry.test.web;

import java.io.Serializable;

/**
 * 故障转移演示任务参数。
 *
 * @Author Codex
 * @Version FailoverDemoParam.java, v 0.1 2026年09月26日 Codex
 */
public class FailoverDemoParam implements Serializable {

    private static final long serialVersionUID = 1L;

    private String runId;

    public FailoverDemoParam() {
    }

    public FailoverDemoParam(String runId) {
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }
}
