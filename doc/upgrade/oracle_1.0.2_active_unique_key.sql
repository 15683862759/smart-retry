-- ============================================================
-- 升级说明：把 unique_key 的全局唯一去重调整为“仅活跃任务唯一”。
-- 适用版本：smart-retry 1.0.2
-- 执行时机：业务低峰期；执行前请确认当前唯一索引为 uk_unique_key。
-- 注意：Oracle 一次性脚本不使用 IF EXISTS，重复执行前需先清理已创建对象。
-- ============================================================

-- 旧索引会把终态历史也视为冲突，导致同一业务参数无法再次创建任务。
DROP INDEX uk_unique_key;

-- active_flag 不由应用写入，统一由触发器根据状态和剩余次数维护。
ALTER TABLE retry_task ADD active_flag NUMBER(1);

-- 兼容升级前已存在的数据。
UPDATE retry_task
SET active_flag = CASE
    WHEN status IN (0, 1, 3) AND retry_num >= 1 THEN 1
    ELSE NULL
END;
COMMIT;

-- 组合唯一索引中 active_flag 为 NULL 时不参与唯一性判断，因此允许多条终态历史。
CREATE UNIQUE INDEX uk_unique_key ON retry_task(unique_key, active_flag);

CREATE OR REPLACE TRIGGER trg_retry_task_active_flag
BEFORE INSERT OR UPDATE ON retry_task
FOR EACH ROW
BEGIN
    IF :NEW.status IN (0, 1, 3) AND :NEW.retry_num >= 1 THEN
        :NEW.active_flag := 1;
    ELSE
        :NEW.active_flag := NULL;
    END IF;
END;
/
