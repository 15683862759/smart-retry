package com.smart.retry.test;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @Author xiaoqiang
 * @Version AbstractTest.java, v 0.1 2025年02月19日 00:49 xiaoqiang
 * @Description: TODO
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = SmartTestApplication.class,
        properties = {
                "server.port=7099",
                "spring.smart-retry.taskFindInterval=1",
                "spring.smart-retry.health.interval=1",
                "spring.smart-retry.health.timeout=2",
                "spring.smart-retry.health.scanInterval=1"
        }
)
public abstract class AbstractTest {
}
