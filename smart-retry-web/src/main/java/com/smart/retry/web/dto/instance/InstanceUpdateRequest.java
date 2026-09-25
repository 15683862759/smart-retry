package com.smart.retry.web.dto.instance;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;

/**
 * 实例更新请求
 */
@Data
public class InstanceUpdateRequest {
    
    private Long id;
    
    @NotBlank(message = "instanceId不能为空")
    @Pattern(
            regexp = "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}"
                    + "(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)"
                    + ":(6553[0-5]|655[0-2]\\d|65[0-4]\\d{2}|6[0-4]\\d{3}|[1-5]\\d{4}|[1-9]\\d{0,3})$",
            message = "instanceId必须是ip:port格式，例如：192.168.1.100:8080")
    private String instanceId;
}
