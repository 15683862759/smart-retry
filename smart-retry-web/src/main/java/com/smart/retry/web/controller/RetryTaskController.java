package com.smart.retry.web.controller;

import com.smart.retry.web.dto.PageResult;
import com.smart.retry.web.dto.Result;
import com.smart.retry.web.dto.task.*;
import com.smart.retry.web.service.RetryTaskService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.List;

/**
 * 任务管理控制器
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class RetryTaskController {
    
    private final RetryTaskService retryTaskService;
    
    /**
     * 分页查询任务列表
     */
    @PostMapping("/query")
    public Result<PageResult<TaskVO>> queryTasks(@Valid @RequestBody TaskQueryRequest request) {
        PageResult<TaskVO> result = retryTaskService.queryTasks(request);
        return Result.success(result);
    }
    
    /**
     * 创建任务
     */
    @PostMapping("/create")
    public Result<Long> createTask(@Valid @RequestBody TaskCreateRequest request) {
        Long taskId = retryTaskService.createTask(request);
        return Result.success(taskId);
    }
    
    /**
     * 更新任务
     */
    @PutMapping("/update")
    public Result<Void> updateTask(@Valid @RequestBody TaskUpdateRequest request) throws ParseException {
        retryTaskService.updateTask(request);
        return Result.success();
    }
    
    /**
     * 删除任务
     */
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteTask(@PathVariable("id") Long id) {
        retryTaskService.deleteTask(id);
        return Result.success();
    }
    
    /**
     * 批量删除任务
     */
    @DeleteMapping("/batch-delete")
    public Result<Void> batchDeleteTasks(@RequestBody List<Long> ids) {
        retryTaskService.batchDeleteTasks(ids);
        return Result.success();
    }
    
    /**
     * 获取分片选择列表
     */
    @GetMapping("/sharding-options")
    public Result<List<ShardingOptionVO>> getShardingOptions() {
        List<ShardingOptionVO> options = retryTaskService.getShardingOptions();
        return Result.success(options);
    }
}
