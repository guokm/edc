package com.example.edc.operator.controller;

import com.example.edc.operator.dto.LoginRequest;
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
@RequestMapping("/api/auth")
public class AuthController {
    private final OperatorService operatorService;

    public AuthController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 运营账号登录，生成后续业务接口使用的 X-Operator-Token。
     *
     * @param request 登录请求，包含用户名与密码。
     * @return 登录令牌、登录用户与令牌过期时间。
     */
    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest request) {
        return operatorService.login(request);
    }

    /**
     * 查询当前登录用户，用于前端恢复会话和判断角色权限。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 当前登录用户与令牌过期时间。
     */
    @GetMapping("/me")
    public Object me(@RequestHeader(value = "X-Operator-Token", required = false) String token) {
        return operatorService.currentUser(token);
    }

    /**
     * 退出当前登录会话，撤销 X-Operator-Token。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 退出结果。
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "X-Operator-Token", required = false) String token) {
        return operatorService.logout(token);
    }
}
