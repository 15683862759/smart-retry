package com.smart.retry.web.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 分页请求参数
 */
@Data
public class PageRequest {

    /**
     * 页码，从 1 开始
     */
    @NotNull
    @Min(1)
    private Integer pageNum = 1;

    /**
     * 每页条数，限制最大值避免一次拉取过多数据
     */
    @NotNull
    @Min(1)
    @Max(200)
    private Integer pageSize = 10;

    public Integer getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
