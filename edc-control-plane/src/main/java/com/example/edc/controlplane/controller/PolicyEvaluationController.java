package com.example.edc.controlplane.controller;

import com.example.edc.controlplane.dto.PolicyEvaluationRequest;
import com.example.edc.controlplane.service.PolicyEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/policies")
public class PolicyEvaluationController {
    private final PolicyEvaluationService policyEvaluationService;

    public PolicyEvaluationController(PolicyEvaluationService policyEvaluationService) {
        this.policyEvaluationService = policyEvaluationService;
    }

    /**
     * 执行策略约束匹配校验。
     *
     * @param request 策略评估请求。
     * @return 校验结果（allowed 等字段）。
     */
    @PostMapping("/evaluate")
    public Map<String, Object> evaluate(@Valid @RequestBody PolicyEvaluationRequest request) {
        return policyEvaluationService.evaluate(request);
    }
}
