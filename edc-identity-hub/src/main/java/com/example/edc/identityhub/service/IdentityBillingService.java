package com.example.edc.identityhub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.PAYMENT_REQUIRED;

@Service
public class IdentityBillingService {
    private static final String DEFAULT_PARTICIPANT = "participant-a";

    private final RestClient restClient;
    private final String operatorBaseUrl;

    public IdentityBillingService(@Value("${edc.operator.base-url:http://localhost:8186}") String operatorBaseUrl) {
        this.restClient = RestClient.builder().build();
        this.operatorBaseUrl = operatorBaseUrl;
    }

    /**
     * 调用运营服务进行按次计费校验，并在通过时消耗一次额度。
     *
     * @param participantId 参与方 ID。
     * @param serviceCode 服务编码。
     */
    public void checkQuota(String participantId, String serviceCode) {
        var participant = StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT;
        try {
            var response = restClient.post()
                    .uri(operatorBaseUrl + "/api/billing/usage/check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("participantId", participant, "serviceCode", serviceCode))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            var allowed = response != null && Boolean.TRUE.equals(response.get("allowed"));
            if (!allowed) {
                throw new ResponseStatusException(PAYMENT_REQUIRED,
                        "Usage quota exhausted for participant=" + participant + ", service=" + serviceCode);
            }
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Billing service unavailable: " + ex.getMessage(), ex);
        }
    }
}
