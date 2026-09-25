package com.smart.retry.web.dto.task;

import com.smart.retry.web.dto.PageRequest;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务创建请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskCreateRequest extends PageRequest {
    
    @NotBlank(message = "taskCode不能为空")
    private String taskCode;
    
    @NotBlank(message = "taskDesc不能为空")
    private String taskDesc;
    
    @NotNull(message = "retryNum不能为空")
    @Min(value = 1, message = "retryNum必须大于0")
    private Integer retryNum;
    
    @NotNull(message = "delaySecond不能为空")
    @Min(value = 1, message = "delaySecond必须大于0")
    private Integer delaySecond = 100;
    
    @NotNull(message = "intervalSecond不能为空")
    @Min(value = 1, message = "intervalSecond必须大于0")
    private Integer intervalSecond = 600;
    
    @NotBlank(message = "param不能为空")
    private String param;
    
    @NotNull(message = "shardingKey不能为空")
    private Long shardingKey;
    
    /**
     * 下次执行时间策略：1-固定间隔 2-递增 3-斐波那契 4-退避
     */
    @NotNull(message = "nextPlanTimeStrategy不能为空")
    @Min(value = 1, message = "nextPlanTimeStrategy必须在1-4之间")
    @Max(value = 4, message = "nextPlanTimeStrategy必须在1-4之间")
    private Integer nextPlanTimeStrategy = 1;
}
