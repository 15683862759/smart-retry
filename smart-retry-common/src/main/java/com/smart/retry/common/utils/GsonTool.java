package com.smart.retry.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author gao.gwq
 * @version 1.0
 * @date 2022/4/18  13:52
 * @Description: Gson 工具类。提供忽略空值、保留空值、集合和泛型对象的
 * 序列化/反序列化能力，统一日期格式和复杂 Map key 处理规则。
 */
public class GsonTool {
    private static final Gson GSON;
    private static final Gson GSON_NULL; // 不过滤空值

    static {
        GsonBuilder gsonBuilder = createBaseBuilder(false);
        TypeAdapter<Date> dateTypeAdapter = gsonBuilder.create().getAdapter(Date.class);

        // 确保日期适配器遇到 null 时不抛错。
        TypeAdapter<Date> safeDateTypeAdapter = dateTypeAdapter.nullSafe();
        GSON = gsonBuilder
                .registerTypeAdapter(Date.class, safeDateTypeAdapter)
                .create();
        GSON_NULL = createBaseBuilder(true)
                .registerTypeAdapter(Date.class, safeDateTypeAdapter)
                .create();
    }

    /**
     * 创建基础 Gson 构建器，统一两个解析器的日期格式和复杂 Map key 规则。
     *
     * @param serializeNulls 是否保留 null 字段
     * @return 已完成基础配置的构建器
     */
    private static GsonBuilder createBaseBuilder(boolean serializeNulls) {
        GsonBuilder builder = new GsonBuilder();
        if (serializeNulls) {
            builder.serializeNulls();
        }
        return builder.enableComplexMapKeySerialization()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .disableHtmlEscaping();
    }

    //获取gson解析器
    public static Gson getGson() {
        return GSON;
    }

    //获取gson解析器 有空值 解析
    public static Gson getWriteNullGson() {
        return GSON_NULL;
    }

    /**
     * 根据对象返回json  过滤空值字段
     */
    public static String toJsonStringIgnoreNull(Object object) {
        return GSON.toJson(object);
    }

    /**
     * 根据对象返回json  不过滤空值字段
     */
    public static String toJsonString(Object object) {
        return GSON_NULL.toJson(object);
    }

    /**
     * 将字符串转化对象
     *
     * @param json     源字符串
     * @param classOfT 目标对象类型
     * @param <T>
     * @return
     */
    public static <T> T strToJavaBean(String json, Class<T> classOfT) {
        return GSON.fromJson(json, classOfT);
    }

    /**
     * 将json转化为对应的实体对象
     * new TypeToken<List<T>>() {}.getType()
     * new TypeToken<Map<String, T>>() {}.getType()
     * new TypeToken<List<Map<String, T>>>() {}.getType()
     */
    public static <T> T fromJson(String json, Type typeOfT) {


        return GSON.fromJson(json, typeOfT);
    }

    /**
     * 将 JSON 数组转换为指定元素类型的 List。
     *
     * <p>不能使用 {@code new TypeToken<List<T>>(){}.getType()}：方法内部泛型 T
     * 在运行期会被擦除，Gson 实际按 List<Object> 解析。这里把调用方传入的
     * 元素类型交给 Gson，确保数值、对象等字段按目标类型还原。
     *
     * @param gsonString JSON 数组字符串
     * @param cls        List 元素类型
     * @return 元素类型为 cls 的 List
     */
    public static <T> List<T> strToList(String gsonString, Class<T> cls) {
        return GSON.fromJson(gsonString, TypeToken.getParameterized(List.class, cls).getType());
    }

    /**
     * 转成list中有map的
     *
     * @param gsonString
     * @return
     */
    public static <T> List<Map<String, T>> strToListMaps(String gsonString) {
        return GSON.fromJson(gsonString, new TypeToken<List<Map<String, String>>>() {
        }.getType());
    }

    /**
     * 转成map
     *
     * @param gsonString
     * @return
     */
    public static <T> Map<String, T> strToMaps(String gsonString) {
        return GSON.fromJson(gsonString, new TypeToken<Map<String, T>>() {
        }.getType());
    }

    /**
     * json 转成 特定的 rawClass<classOfT> 的Object
     *
     * @param json
     * @param classOfT
     * @param argClassOfT
     * @return
     */
    public static <T> T fromJson(String json, Class<T> classOfT, Class argClassOfT) {

        Type type = new ParameterizedType4ReturnT(classOfT, new Class[]{argClassOfT});
        return GSON.fromJson(json, type);
    }

    public static class ParameterizedType4ReturnT implements ParameterizedType {
        private final Class raw;
        private final Type[] args;

        public ParameterizedType4ReturnT(Class raw, Type[] args) {
            this.raw = raw;
            this.args = args != null ? args : new Type[0];
        }

        @Override
        public Type[] getActualTypeArguments() {
            return args;
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    /**
     * 将 JSON 数组转换为指定元素类型的 List。
     *
     * <p>与 {@link #strToList(String, Class)} 保持同一类型构造规则，
     * 避免泛型擦除导致调用方在读取元素时发生类型转换异常。
     *
     * @param json      JSON 数组字符串
     * @param classOfT List 元素类型
     * @return 元素类型为 classOfT 的 List
     */
    public static <T> List<T> fromJsonList(String json, Class<T> classOfT) {
        return GSON.fromJson(json, TypeToken.getParameterized(List.class, classOfT).getType());
    }


}
