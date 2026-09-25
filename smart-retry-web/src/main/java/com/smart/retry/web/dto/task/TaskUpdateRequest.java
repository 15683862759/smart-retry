package com.smart.retry.web.dto.task;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import lombok.Data;

/**
 * 任务更新请求
 */
@Data
public class TaskUpdateRequest {
    
    @NotNull(message = "id不能为空")
    private Long id;
    
    private String nextPlanTime;
    
    @Min(value = 1, message = "retryNum必须大于0")
    private Integer retryNum;
    
    private String param;
    
    @Min(value = 0, message = "status必须在0-3之间")
    @Max(value = 3, message = "status必须在0-3之间")
    private Integer status;
}
