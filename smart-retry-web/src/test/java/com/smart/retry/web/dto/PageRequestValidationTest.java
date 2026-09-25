package com.smart.retry.web.dto;

import com.smart.retry.web.controller.RetryInstanceController;
import com.smart.retry.web.controller.RetryTaskController;
import org.junit.Before;
import org.junit.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PageRequestValidationTest {

    private Validator validator;

    @Before
    public void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testPageRequestRejectsInvalidPagination() {
        PageRequest request = new PageRequest();
        request.setPageNum(0);
        request.setPageSize(0);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    public void testPageRequestRejectsOversizedPageSize() {
        PageRequest request = new PageRequest();
        request.setPageNum(1);
        request.setPageSize(201);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    public void testPageRequestRejectsOversizedPageNum() {
        PageRequest request = new PageRequest();
        request.setPageNum(1_000_001);
        request.setPageSize(200);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    public void testQueryControllerMethodsEnableValidation() throws Exception {
        Method queryTasks = RetryTaskController.class.getMethod(
                "queryTasks", com.smart.retry.web.dto.task.TaskQueryRequest.class);
        Method queryInstances = RetryInstanceController.class.getMethod(
                "queryInstances", com.smart.retry.web.dto.instance.InstanceQueryRequest.class);

        assertTrue(hasValidAnnotation(queryTasks.getParameterAnnotations()[0]));
        assertTrue(hasValidAnnotation(queryInstances.getParameterAnnotations()[0]));
    }

    private boolean hasValidAnnotation(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .anyMatch(annotation -> annotation.annotationType() == Valid.class);
    }
}
