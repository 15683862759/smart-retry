package com.smart.retry.common.serializer;

/**
 * @Author xiaoqiang
 * @Version SerializerObject.java, v 0.1 2025年02月12日 15:29 xiaoqiang
 * @Description: 方法参数序列化载体。保存参数名、参数位置、类型和 JSON 字符串，
 * 重试执行时按 index 和 className 还原方法调用参数。
 */
public class SerializerObject implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 参数名称
     */
    private String paramName;

    /**
     * 参数值
     */
    private String paramVal;

    /**
     * 参数时序，从 0 开始，必须与原方法参数下标一致。
     */
    private Integer index;

    /**
     * 参数对象名称
     */
    private String className;

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getParamVal() {
        return paramVal;
    }

    public void setParamVal(String paramVal) {
        this.paramVal = paramVal;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

}
