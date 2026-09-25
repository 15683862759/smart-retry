package com.smart.retry.web.dto;

import com.smart.retry.web.dto.instance.InstanceUpdateRequest;
import org.junit.Before;
import org.junit.Test;

import javax.validation.Validation;
import javax.validation.Validator;

import static org.junit.Assert.assertFalse;

public class InstanceUpdateRequestValidationTest {

    private Validator validator;

    @Before
    public void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void testInstanceUpdateRejectsMissingId() {
        InstanceUpdateRequest request = new InstanceUpdateRequest();
        request.setInstanceId("192.168.1.100:8080");

        assertFalse(validator.validate(request).isEmpty());
    }
}
