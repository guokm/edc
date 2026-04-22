package com.example.edc.operator.controller;

import com.example.edc.operator.dto.UserAccountRequest;
import com.example.edc.operator.service.OperatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserAccountController {
    private final OperatorService operatorService;

    public UserAccountController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 创建运营账号，仅平台管理员可操作，返回结果不包含密码摘要。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @param request 账号创建请求，包含用户名、角色、组织、参与方与初始密码。
     * @return 新创建的账号信息。
     */
    @PostMapping
    public Object create(
            @RequestHeader(value = "X-Operator-Token", required = false) String token,
            @Valid @RequestBody UserAccountRequest request) {
        return operatorService.createUserAccount(token, request);
    }

    /**
     * 查询运营账号列表，按当前登录角色自动控制可见范围。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 账号列表，不包含密码摘要。
     */
    @GetMapping
    public Map<String, Object> list(@RequestHeader(value = "X-Operator-Token", required = false) String token) {
        return Map.of("items", operatorService.listUserAccounts(token));
    }
}
