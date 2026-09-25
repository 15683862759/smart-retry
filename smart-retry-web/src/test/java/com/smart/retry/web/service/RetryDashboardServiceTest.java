package com.smart.retry.web.service;

import com.smart.retry.web.dao.WebRetryShardingDao;
import com.smart.retry.web.dao.WebRetryTaskDao;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class RetryDashboardServiceTest {

    @Test
    public void testDashboardUsesConfiguredHealthTimeoutForActiveInstances() {
        int[] capturedTimeoutSeconds = new int[1];
        WebRetryShardingDao shardingDao = (WebRetryShardingDao) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WebRetryShardingDao.class},
                (proxy, method, args) -> {
                    if ("countActiveInstances".equals(method.getName())) {
                        capturedTimeoutSeconds[0] = (Integer) args[0];
                        return 1;
                    }
                    return Collections.emptyList();
                });
        WebRetryTaskDao taskDao = (WebRetryTaskDao) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WebRetryTaskDao.class},
                (proxy, method, args) -> "getTaskProcessRate".equals(method.getName()) ? 0.0 : Collections.emptyList());

        new RetryDashboardService(shardingDao, taskDao).getDashboardData();

        assertEquals(240, capturedTimeoutSeconds[0]);
    }
}
