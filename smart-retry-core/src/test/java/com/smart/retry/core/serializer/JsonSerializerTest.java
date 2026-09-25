package com.smart.retry.core.serializer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.smart.retry.common.serializer.SerializerObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class JsonSerializerTest {

    private final JsonSerializer serializer = new JsonSerializer();

    @Test
    public void testSerializerFallsBackWhenParameterNameIsUnavailable() throws Exception {
        Method method = java.util.function.Function.class.getMethod("apply", Object.class);

        String result = serializer.serializer(method, new Object[]{"value"});

        Assertions.assertNotNull(result);
        List<SerializerObject> values = new Gson().fromJson(
                result, new TypeToken<List<SerializerObject>>() {
                }.getType());
        Assertions.assertEquals(1, values.size());
        Assertions.assertEquals("arg0", values.get(0).getParamName());
        Assertions.assertEquals("\"value\"", values.get(0).getParamVal());
    }

    @Test
    public void testDeserializerIgnoresInvalidParameterIndex() throws Exception {
        Method method = getClass().getDeclaredMethod("invoke", String.class, Integer.class);

        SerializerObject valid = serializerObject(1, "1");
        SerializerObject invalid = serializerObject(99, "ignored");
        String payload = new Gson().toJson(Arrays.asList(valid, invalid));

        Object[] result = serializer.deSerializer(method, payload);

        Assertions.assertEquals(2, result.length);
        Assertions.assertNull(result[0]);
        Assertions.assertEquals(Integer.valueOf(1), result[1]);
    }

    private void invoke(String text, Integer number) {
    }

    private SerializerObject serializerObject(int index, String value) {
        SerializerObject object = new SerializerObject();
        object.setIndex(index);
        object.setParamName("arg" + index);
        object.setParamVal(new Gson().toJson(value));
        object.setClassName(String.class.getName());
        return object;
    }
}
