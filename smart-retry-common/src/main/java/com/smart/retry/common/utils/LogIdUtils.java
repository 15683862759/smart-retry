package com.smart.retry.common.utils;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * @Author xiaoqiang
 * @Version LogIdUtils.java, v 0.1 2026年06月15日 traceId
 * @Description: traceId / logId 编码与还原工具。
 * <p>
 * 设计目标：业务侧使用任何 trace 体系（EagleEye / Sleuth / SkyWalking / 自定义）
 * 时，框架把"命中的 MDC key + value"统一编码为 {@code key::value} 写入
 * {@code current_log_id}，执行重试时再按当初那个 key 精准写回 MDC，
 * 避免覆盖业务方线程原有的其它 MDC key。
 */
public final class LogIdUtils {

    /**
     * 数据库字段里 key 与 value 的拼接分隔符。
     */
    public static final String ENCODED_SEPARATOR = "::";

    /**
     * 按命中率从高到低维护的常见 traceId MDC key 数组。
     * 顺序敏感：优先返回最早命中的 key，便于兼容多套 trace 体系并存的环境。
     */
    public static final String[] MDC_TRACE_KEYS = new String[]{
            "traceId",
            "X-B3-TraceId",
            "TRACE_ID",
            "EAGLEEYE_TRACEID",
            "sw:traceId",
            "SW_TRACE_ID",
            "TID",
            "trace_id",
            "requestId",
            "REQUEST_ID",
            "X-Request-Id",
            "X-Trace-Id"
    };

    private LogIdUtils() {
    }

    /**
     * 命中的 MDC key/value 封装。
     */
    public static final class LogIdLookup {
        private final String key;
        private final String value;

        public LogIdLookup(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public static LogIdLookup empty() {
            return new LogIdLookup(null, null);
        }

        public boolean isPresent() {
            return key != null && value != null && !value.isEmpty();
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 拿当前线程 traceId，兜底返回 {@code UNKNOWN-<UUID>}。
     *
     * @return 当前 traceId
     */
    public static String getCurrentLogId() {
        LogIdLookup lookup = getCurrentLogIdAndKey();
        if (lookup.isPresent()) {
            return lookup.getValue();
        }
        return "UNKNOWN-" + UUID.randomUUID().toString();
    }

    /**
     * 按 MDC_TRACE_KEYS 顺序遍历，返回第一个非空命中。
     *
     * @return 命中记录；若全部为空返回 {@link LogIdLookup#empty()}
     */
    public static LogIdLookup getCurrentLogIdAndKey() {
        for (String key : MDC_TRACE_KEYS) {
            String value = MDC.get(key);
            if (value != null && !value.isEmpty()) {
                return new LogIdLookup(key, value);
            }
        }
        return LogIdLookup.empty();
    }

    /**
     * 把命中的 MDC key + value 拼成 {@code key::value} 形式。
     * key 为空时直接返回 value。
     *
     * @param key   MDC key，可为空
     * @param value traceId 值
     * @return 编码后的字符串
     */
    public static String encode(String key, String value) {
        if (value == null) {
            return "";
        }
        if (key == null || key.isEmpty()) {
            return value;
        }
        return key + ENCODED_SEPARATOR + value;
    }

    /**
     * 把 {@code current_log_id} 中的编码字符串写回 MDC，返回写入了哪个 key。
     * <ul>
     *     <li>字符串含 {@code ::}：按 {@code key::value} 解析后 put</li>
     *     <li>否则视为裸 traceId（兼容历史 BIGINT/纯字符串数据）：按 MDC_TRACE_KEYS
     *         顺序挑第一个尚未占用的 key 写入，避免覆盖业务线程原有的 value</li>
     *     <li>字符串为空或 null：跳过写 MDC，返回 null</li>
     * </ul>
     *
     * @param encoded 数据库中读取的编码 traceId
     * @return 实际写入的 MDC key；未写入时返回 null
     */
    public static String restoreFromEncoded(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        int idx = encoded.indexOf(ENCODED_SEPARATOR);
        if (idx > 0 && idx < encoded.length() - ENCODED_SEPARATOR.length()) {
            String key = encoded.substring(0, idx);
            String value = encoded.substring(idx + ENCODED_SEPARATOR.length());
            MDC.put(key, value);
            return key;
        }
        // 兜底：兼容老数据（裸 traceId），按预定义顺序找第一个空位
        for (String key : MDC_TRACE_KEYS) {
            if (MDC.get(key) == null) {
                MDC.put(key, encoded);
                return key;
            }
        }
        return null;
    }
}
