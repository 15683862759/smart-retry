package com.smart.retry.web.service;

import com.smart.retry.common.RetryTaskEnqueuer;
import com.smart.retry.web.dao.WebRetryShardingDao;
import com.smart.retry.web.dao.WebRetryTaskDao;
import com.smart.retry.web.dto.task.ShardingOptionVO;
import com.smart.retry.web.entity.RetryShardingDO;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务管理服务测试。
 *
 * @Author Codex
 * @Version RetryTaskServiceTest.java, v 0.1 2026年09月26日 02:44 Codex
 * @Description: 验证人工创建任务的分片选项不会被分页大小截断。
 */
public class RetryTaskServiceTest {

    @Test
    public void getShardingOptionsLoadsEveryPage() {
        List<RetryShardingDO> firstPage = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            firstPage.add(sharding(1001 - i, "instance-" + i));
        }
        List<RetryShardingDO> secondPage = new ArrayList<>();
        secondPage.add(sharding(1, "last-instance"));

        WebRetryShardingDao shardingDao = proxyShardingDao(firstPage, secondPage);
        WebRetryTaskDao taskDao = (WebRetryTaskDao) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WebRetryTaskDao.class},
                (proxy, method, args) -> null);
        ObjectProvider<RetryTaskEnqueuer> enqueuerProvider = (ObjectProvider<RetryTaskEnqueuer>) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ObjectProvider.class},
                (proxy, method, args) -> null);

        List<ShardingOptionVO> options = new RetryTaskService(taskDao, shardingDao, enqueuerProvider)
                .getShardingOptions();

        Assert.assertEquals(1001, options.size());
        Assert.assertTrue(hasInstance(options, "last-instance"));
    }

    private WebRetryShardingDao proxyShardingDao(List<RetryShardingDO> firstPage,
                                                  List<RetryShardingDO> secondPage) {
        return (WebRetryShardingDao) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WebRetryShardingDao.class},
                (proxy, method, args) -> {
                    if ("selectAllWithPage".equals(method.getName())) {
                        int offset = (Integer) args[0];
                        return offset == 0 ? firstPage : secondPage;
                    }
                    return null;
                });
    }

    private RetryShardingDO sharding(long id, String instanceId) {
        RetryShardingDO sharding = new RetryShardingDO();
        sharding.setId(id);
        sharding.setInstanceId(instanceId);
        return sharding;
    }

    private boolean hasInstance(List<ShardingOptionVO> options, String instanceId) {
        for (ShardingOptionVO option : options) {
            if (instanceId.equals(option.getInstanceId())) {
                return true;
            }
        }
        return false;
    }
}
