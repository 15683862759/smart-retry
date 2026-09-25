package com.smart.retry.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Gson 工具泛型反序列化测试。
 *
 * @Author Codex
 * @Version GsonToolTest.java, v 0.1 2026年09月26日 00:29 Codex
 * @Description: 验证 List 泛型方法使用调用方传入的元素类型还原数据，
 * 避免运行期泛型擦除导致调用方拿到 Double 或 Object 后触发类型转换异常。
 */
public class GsonToolTest {

    @Test
    public void strToListUsesDeclaredElementType() {
        List<Integer> values = GsonTool.strToList("[1,2]", Integer.class);

        Assert.assertEquals(Integer.valueOf(1), values.get(0));
        Assert.assertEquals(Integer.valueOf(2), values.get(1));
    }

    @Test
    public void fromJsonListUsesDeclaredElementType() {
        List<Integer> values = GsonTool.fromJsonList("[3,4]", Integer.class);

        Assert.assertEquals(Integer.valueOf(3), values.get(0));
        Assert.assertEquals(Integer.valueOf(4), values.get(1));
    }
}
