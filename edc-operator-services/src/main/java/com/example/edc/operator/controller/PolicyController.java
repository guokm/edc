package com.example.edc.operator.controller;

import com.example.edc.operator.dto.PolicyRequest;
import com.example.edc.operator.service.OperatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    private final OperatorService operatorService;

    public PolicyController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 创建治理策略。
     *
     * @param request 策略创建请求。
     * @return 策略信息。
     */
    @PostMapping
    public Object create(@Valid @RequestBody PolicyRequest request) {
        return operatorService.createPolicy(request);
    }

    /**
     * 根据策略 ID 查询详情。
     *
     * @param id 策略 ID。
     * @return 策略信息。
     */
    @GetMapping("/{id}")
    public Object get(@PathVariable("id") String id) {
        return operatorService.getPolicy(id);
    }

    /**
     * 查询策略列表。
     *
     * @return 策略集合。
     */
    @GetMapping
    public Map<String, Object> list() {
        return Map.of("items", operatorService.listPolicies());
    }
}
