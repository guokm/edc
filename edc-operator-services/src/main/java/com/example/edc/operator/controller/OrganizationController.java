package com.example.edc.operator.controller;

import com.example.edc.operator.dto.OrganizationRequest;
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
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OperatorService operatorService;

    public OrganizationController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 创建企业组织主数据，仅平台管理员可操作。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @param request 组织创建请求，包含企业名称、信用代码与联系人信息。
     * @return 新创建的组织信息。
     */
    @PostMapping
    public Object create(
            @RequestHeader(value = "X-Operator-Token", required = false) String token,
            @Valid @RequestBody OrganizationRequest request) {
        return operatorService.createOrganization(token, request);
    }

    /**
     * 查询企业组织列表，按当前登录角色自动控制可见范围。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 组织列表。
     */
    @GetMapping
    public Map<String, Object> list(@RequestHeader(value = "X-Operator-Token", required = false) String token) {
        return Map.of("items", operatorService.listOrganizations(token));
    }
}
