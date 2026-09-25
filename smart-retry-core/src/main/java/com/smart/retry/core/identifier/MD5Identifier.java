package com.smart.retry.core.identifier;

import com.smart.retry.common.identifier.Identifier;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * @Author xiaoqiang
 * @Version MD5Identifier.java, v 0.1 2025年02月13日 19:15 xiaoqiang
 * @Description: MD5 唯一标识生成器。用任务编码和参数 JSON 生成稳定 unique_key，
 * 用于数据库唯一约束和内存去重。
 */
public class MD5Identifier implements Identifier {

    @Override
    /**
     * 使用 MD5(taskCode:args) 生成唯一标识。
     *
     * @param taskCode 任务编码
     * @param argStr   序列化参数
     * @return 32 位十六进制唯一标识
     */
    public String identify( String taskCode, String argStr) {
        return DigestUtils.md5Hex( taskCode +":"+ argStr);
    }
}
