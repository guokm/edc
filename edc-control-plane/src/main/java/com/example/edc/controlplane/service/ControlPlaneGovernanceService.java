package com.example.edc.controlplane.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.PAYMENT_REQUIRED;

@Service
public class ControlPlaneGovernanceService {
    private static final String DEFAULT_PARTICIPANT = "participant-a";

    private final RestClient restClient;
    private final String operatorBaseUrl;
    private final String identityHubBaseUrl;

    public ControlPlaneGovernanceService(
            @Value("${edc.operator.base-url:http://localhost:8186}") String operatorBaseUrl,
            @Value("${edc.identity-hub.base-url:http://localhost:8183}") String identityHubBaseUrl) {
        this.restClient = RestClient.builder().build();
        this.operatorBaseUrl = operatorBaseUrl;
        this.identityHubBaseUrl = identityHubBaseUrl;
    }

    /**
     * 校验参与方是否存在当前有效的 ACTIVE 会员记录。
     *
     * @param participantId 参与方 ID。
     */
    public void ensureActiveMembership(String participantId) {
        var participant = resolveParticipant(participantId);
        try {
            queryActiveMembership(participant);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        FORBIDDEN,
                        "Active membership required for participant=" + participant
                );
            }
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Membership validation failed: " + ex.getMessage(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Membership validation service unavailable: " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * 校验参与方是否具备“签发+展示校验”后的资格。
     *
     * @param participantId 参与方 ID。
     */
    public void ensureCredentialQualification(String participantId) {
        var participant = resolveParticipant(participantId);
        try {
            var response = queryQualificationStatus(participant, null);
            var qualified = response != null && Boolean.TRUE.equals(response.get("qualified"));
            if (!qualified) {
                var reason = response == null ? "UNKNOWN" : String.valueOf(response.getOrDefault("reason", "UNKNOWN"));
                throw new ResponseStatusException(
                        FORBIDDEN,
                        "Credential qualification required for participant=" + participant + ", reason=" + reason
                );
            }
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Credential qualification validation failed: " + ex.getMessage(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Credential qualification service unavailable: " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * 调用运营服务执行按次计费校验，通过时自动扣减一次额度。
     *
     * @param participantId 参与方 ID。
     * @param serviceCode 服务编码。
     */
    public void checkQuota(String participantId, String serviceCode) {
        var participant = resolveParticipant(participantId);
        try {
            var response = consumeQuota(participant, serviceCode);
            var allowed = response != null && Boolean.TRUE.equals(response.get("allowed"));
            if (!allowed) {
                throw new ResponseStatusException(
                        PAYMENT_REQUIRED,
                        "Usage quota exhausted for participant=" + participant + ", service=" + serviceCode
                );
            }
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Billing validation failed: " + ex.getMessage(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Billing service unavailable: " + ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * 查询参与方当前有效 ACTIVE 会员记录。
     *
     * @param participantId 参与方 ID。
     * @return 会员信息快照。
     */
    public Map<String, Object> queryActiveMembership(String participantId) {
        var participant = resolveParticipant(participantId);
        return restClient.get()
                .uri(operatorBaseUrl + "/api/memberships/active?participantId={participantId}", participant)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    /**
     * 查询参与方签发资格快照。
     *
     * @param participantId 参与方 ID。
     * @param audience 受众参与方 ID，可为空。
     * @return 签发资格快照。
     */
    public Map<String, Object> queryQualificationStatus(String participantId, String audience) {
        var participant = resolveParticipant(participantId);
        var request = restClient.get();
        var uri = identityHubBaseUrl + "/api/dcp/qualification?participantId={participantId}";
        if (StringUtils.hasText(audience)) {
            uri += "&audience={audience}";
            return request.uri(uri, participant, audience)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        }
        return request.uri(uri, participant)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    /**
     * 查询按次计费状态，不执行扣减。
     *
     * @param participantId 参与方 ID。
     * @param serviceCode 服务编码。
     * @return 计费状态快照。
     */
    public Map<String, Object> queryUsageStatus(String participantId, String serviceCode) {
        var participant = resolveParticipant(participantId);
        return restClient.get()
                .uri(
                        operatorBaseUrl + "/api/billing/usage/status?participantId={participantId}&serviceCode={serviceCode}",
                        participant,
                        serviceCode
                )
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    /**
     * 执行按次计费校验并扣减额度。
     *
     * @param participantId 参与方 ID。
     * @param serviceCode 服务编码。
     * @return 扣减结果。
     */
    public Map<String, Object> consumeQuota(String participantId, String serviceCode) {
        var participant = resolveParticipant(participantId);
        return restClient.post()
                .uri(operatorBaseUrl + "/api/billing/usage/check")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("participantId", participant, "serviceCode", serviceCode))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    /**
     * 创建账单记录（用于协议/资产维度计费落库）。
     *
     * @param agreementId 协议 ID。
     * @param pricingModel 计费模型编码。
     * @param amount 金额。
     * @param currency 币种。
     * @param periodStart 计费周期开始时间。
     * @param periodEnd 计费周期结束时间。
     * @return 账单记录。
     */
    public Map<String, Object> createBillingRecord(
            String agreementId,
            String pricingModel,
            double amount,
            String currency,
            Instant periodStart,
            Instant periodEnd) {
        return restClient.post()
                .uri(operatorBaseUrl + "/api/billing/records")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "agreementId", agreementId,
                        "pricingModel", pricingModel,
                        "amount", amount,
                        "currency", currency,
                        "periodStart", periodStart == null ? Instant.now().toString() : periodStart.toString(),
                        "periodEnd", periodEnd == null ? Instant.now().toString() : periodEnd.toString()
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    /**
     * 写入审计事件（失败时抛出异常，由调用方决定是否忽略）。
     *
     * @param eventType 事件类型。
     * @param actorId 操作主体。
     * @param payload 事件载荷。
     * @param signature 审计签名。
     * @return 审计事件记录。
     */
    public Map<String, Object> createAuditEvent(
            String eventType,
            String actorId,
            Map<String, Object> payload,
            String signature) {
        return restClient.post()
                .uri(operatorBaseUrl + "/api/audit/events")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "eventType", eventType,
                        "actorId", actorId,
                        "payload", payload == null ? Map.of() : payload,
                        "signature", StringUtils.hasText(signature) ? signature : "unsigned"
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });
    }

    private String resolveParticipant(String participantId) {
        return StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT;
    }
}
