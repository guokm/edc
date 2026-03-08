package com.example.edc.controlplane.service;

import com.example.edc.controlplane.dto.PolicyEvaluationRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class PolicyEvaluationService {

    /**
     * 执行策略评估，逐项比较 claims 与 constraints 是否匹配。
     *
     * @param request 策略评估请求。
     * @return 评估结果，包含是否允许访问。
     */
    public Map<String, Object> evaluate(PolicyEvaluationRequest request) {
        var constraints = request.constraints() == null ? Map.<String, Object>of() : request.constraints();
        var claims = request.claims() == null ? Map.<String, Object>of() : request.claims();
        var allowed = constraints.entrySet().stream()
                .allMatch(entry -> Objects.equals(claims.get(entry.getKey()), entry.getValue()));

        return Map.of(
                "policyId", request.policyId(),
                "allowed", allowed,
                "matchedConstraints", constraints.size()
        );
    }
}
