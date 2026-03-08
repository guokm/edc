package com.example.edc.controlplane.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
public class ControlPlaneAccessService {
    private static final String OPERATOR_PARTICIPANT = "operator";

    private final String orchestrationDemoToken;

    public ControlPlaneAccessService(
            @Value("${edc.security.orchestration-demo-token:operator-demo-key}") String orchestrationDemoToken) {
        this.orchestrationDemoToken = orchestrationDemoToken;
    }

    /**
     * 校验“编排门禁演示接口”的访问权限。
     *
     * @param participantId 调用方参与方 ID（请求头 X-Participant-Id）。
     * @param operatorToken 运营权限令牌（请求头 X-Operator-Token）。
     */
    public void ensureOrchestrationDemoPermission(String participantId, String operatorToken) {
        if (!OPERATOR_PARTICIPANT.equals(participantId)) {
            throw new ResponseStatusException(FORBIDDEN, "Only operator participant can access orchestration demo API");
        }
        if (!StringUtils.hasText(operatorToken) || !orchestrationDemoToken.equals(operatorToken)) {
            throw new ResponseStatusException(FORBIDDEN, "Invalid X-Operator-Token");
        }
    }
}
