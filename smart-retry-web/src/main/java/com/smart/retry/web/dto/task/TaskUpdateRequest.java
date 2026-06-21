package com.smart.retry.web.dto.task;

import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * 任务更新请求
 */
@Data
public class TaskUpdateRequest {
    
    @NotNull(message = "id不能为空")
    private Long id;
    
    private String nextPlanTime;
    
    private Integer retryNum;
    
    private String param;
    
    private Integer status;
}
