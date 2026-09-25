package com.smart.retry.web.dto.task;

import org.junit.Before;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskRequestValidationTest {

    private Validator validator;

    @Before
    public void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testCreateRequestAcceptsValidValues() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setTaskCode("test-task");
        request.setTaskDesc("test");
        request.setRetryNum(1);
        request.setDelaySecond(1);
        request.setIntervalSecond(1);
        request.setParam("{}");
        request.setShardingKey(1L);
        request.setNextPlanTimeStrategy(1);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    public void testCreateRequestRejectsInvalidRetryAndStrategyValues() {
        TaskCreateRequest request = new TaskCreateRequest();
        request.setTaskCode("test-task");
        request.setTaskDesc("test");
        request.setRetryNum(0);
        request.setDelaySecond(0);
        request.setIntervalSecond(0);
        request.setParam("{}");
        request.setShardingKey(1L);
        request.setNextPlanTimeStrategy(99);

        Set<ConstraintViolation<TaskCreateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    public void testUpdateRequestRejectsInvalidStatusAndRetryNum() {
        TaskUpdateRequest request = new TaskUpdateRequest();
        request.setId(1L);
        request.setRetryNum(0);
        request.setStatus(9);

        Set<ConstraintViolation<TaskUpdateRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }
}
