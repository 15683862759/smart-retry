-- ============================================================
-- 升级说明：把 unique_key 的全局唯一去重调整为“仅活跃任务唯一”。
-- 适用版本：smart-retry 1.0.2
-- 执行时机：业务低峰期；执行前请确认当前唯一索引为 uk_unique_key。
-- ============================================================

-- 旧索引会把终态历史也视为冲突，导致同一业务参数无法再次创建任务。
DROP INDEX IF EXISTS uk_unique_key;

-- active_flag 不由应用写入，统一由触发器根据状态和剩余次数维护。
ALTER TABLE retry_task ADD COLUMN active_flag SMALLINT;

-- 兼容升级前已存在的数据。
UPDATE retry_task
SET active_flag = CASE
    WHEN status IN (0, 1, 3) AND retry_num >= 1 THEN 1
    ELSE NULL
END;

-- 组合唯一索引中 active_flag 为 NULL 时不参与唯一性判断，因此允许多条终态历史。
CREATE UNIQUE INDEX IF NOT EXISTS uk_unique_key ON retry_task(unique_key, active_flag);

CREATE OR REPLACE FUNCTION set_retry_task_active_flag() RETURNS trigger AS $$
BEGIN
    NEW.active_flag := CASE
        WHEN NEW.status IN (0, 1, 3) AND NEW.retry_num >= 1 THEN 1
        ELSE NULL
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_retry_task_active_flag ON retry_task;
CREATE TRIGGER trg_retry_task_active_flag
BEFORE INSERT OR UPDATE ON retry_task
FOR EACH ROW EXECUTE PROCEDURE set_retry_task_active_flag();
