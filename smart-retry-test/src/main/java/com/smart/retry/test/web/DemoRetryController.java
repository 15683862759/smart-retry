package com.smart.retry.test.web;

import com.smart.retry.common.utils.LogIdUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author xiaoqiang
 * @Version DemoRetryController.java, v 0.1 2026年06月15日 traceId
 * @Description: 演示重试入口。触发后业务方法会失败并被框架接管；
 * 响应中回显当前 traceId，便于比对落库字段与日志。
 */
@RestController
@RequestMapping("/demo")
public class DemoRetryController {

    @Autowired
    private DemoRetryService demoRetryService;

    /**
     * 触发演示重试。
     *
     * @param bizNo 业务号
     * @return 异常说明与当前 traceId
     */
    @PostMapping("/retry")
    public String retry(@RequestParam String bizNo) {
        try {
            demoRetryService.alwaysFail(bizNo);
        } catch (IllegalStateException ignore) {
            // 业务按预期失败，不向上抛；由框架完成重试
        }
        return "exception-expected, traceId=" + LogIdUtils.getCurrentLogId();
    }
}
