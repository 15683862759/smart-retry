package com.smart.retry.core.serializer;

import com.google.common.collect.Lists;
import com.google.gson.reflect.TypeToken;
import com.smart.retry.common.serializer.SerializerObject;
import com.smart.retry.common.serializer.SmartSerializer;
import com.smart.retry.common.utils.GsonTool;
import org.springframework.core.DefaultParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @Author xiaoqiang
 * @Version serializer.java, v 0.1 2025年02月13日 18:48 xiaoqiang
 * @Description: JSON 参数序列化默认实现。保存方法参数名、下标、类型和 JSON 值，
 * 重试时按方法签名还原参数数组。
 */
public class JsonSerializer implements SmartSerializer {

    @Override
    /**
     * 将方法参数转换为可持久化的 JSON 列表。
     * 每个参数保存下标、名称、类型和值，便于重试时按签名精确还原。
     *
     * @param method 目标方法
     * @param args   首次调用参数
     * @return 序列化后的 JSON 字符串
     */
    public String serializer(Method method, Object[] args) {
        Parameter[] parameters = method.getParameters();
        List<SerializerObject> objectList = Lists.newArrayList();

        if (parameters == null || parameters.length == 0) {
            return GsonTool.toJsonString(objectList);
        }
        DefaultParameterNameDiscoverer defaultParameterNameDiscoverer = new DefaultParameterNameDiscoverer();
        String[] names = defaultParameterNameDiscoverer.getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            SerializerObject serializerObject = new SerializerObject();
            serializerObject.setIndex(i);
            String paramName = names == null ? null : names[i];
            serializerObject.setParamName(paramName == null ? parameters[i].getName() : paramName);
            serializerObject.setParamVal(GsonTool.toJsonStringIgnoreNull(args[i]));
            serializerObject.setClassName(parameters[i].getParameterizedType().getTypeName());
            objectList.add(serializerObject);
        }

        return GsonTool.toJsonStringIgnoreNull(objectList);
    }

    @Override
    /**
     * 从任务记录还原方法参数数组。
     * 忽略下标为空或越界的脏数据，缺失参数保持 null。
     *
     * @param method        目标方法
     * @param serivlizerVal 数据库中的参数 JSON
     * @return 与原方法参数长度一致的数组
     */
    public Object[] deSerializer(Method method, String serivlizerVal) {

        Parameter[] parameters = method.getParameters();
        if (parameters == null || parameters.length == 0) {
            return new Object[0];
        }

        List<SerializerObject> paramValList = GsonTool.fromJson(serivlizerVal, new TypeToken<List<SerializerObject>>() {
        }.getType());
        Object[] args = new Object[parameters.length];
        for (SerializerObject serializerObject : paramValList) {
            Integer index = serializerObject.getIndex();
            if (index == null || index < 0 || index >= parameters.length) {
                continue;
            }
            Parameter parameter = parameters[index];
            Type type = TypeToken.get(parameter.getParameterizedType()).getType();
            Object objectVal = GsonTool.fromJson(serializerObject.getParamVal(), type);
            args[index] = objectVal;
        }
        return args;
    }
}
