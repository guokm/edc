package com.example.edc.identityhub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.edc.common.model.Credential;
import com.example.edc.identityhub.dto.CredentialRequest;
import com.example.edc.identityhub.dto.DcpPresentationRequest;
import com.example.edc.identityhub.dto.DcpVerificationRequest;
import com.example.edc.identityhub.dto.PresentationRequest;
import com.example.edc.identityhub.entity.IhCredentialEntity;
import com.example.edc.identityhub.entity.IhPresentationEntity;
import com.example.edc.identityhub.mapper.IhCredentialMapper;
import com.example.edc.identityhub.mapper.IhPresentationMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class IdentityHubService {
    private static final String DEFAULT_PARTICIPANT = "participant-a";

    private final IhCredentialMapper credentialMapper;
    private final IhPresentationMapper presentationMapper;
    private final IdentityBillingService identityBillingService;
    private final String issuerServiceBaseUrl;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public IdentityHubService(
            IhCredentialMapper credentialMapper,
            IhPresentationMapper presentationMapper,
            IdentityBillingService identityBillingService,
            ObjectMapper objectMapper,
            @Value("${edc.issuer.base-url:http://localhost:8184}") String issuerServiceBaseUrl) {
        this.credentialMapper = credentialMapper;
        this.presentationMapper = presentationMapper;
        this.identityBillingService = identityBillingService;
        this.issuerServiceBaseUrl = issuerServiceBaseUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    /**
     * 返回当前节点 DID。
     *
     * @return DID 信息。
     */
    public Map<String, Object> getDid() {
        return Map.of("did", "did:web:participant-a.example.com");
    }

    /**
     * 存储身份凭证并返回标准 Credential。
     *
     * @param participantId 参与方 ID。
     * @param request 凭证请求。
     * @return 凭证对象。
     */
    public Credential storeCredential(String participantId, CredentialRequest request) {
        identityBillingService.checkQuota(participantId, "IDENTITY_CREDENTIAL_WRITE");
        var claims = withIssuanceClaim(request.claims(), request.issuanceId());

        var now = LocalDateTime.now();
        var entity = new IhCredentialEntity();
        entity.setId("cred-" + UUID.randomUUID());
        entity.setType(request.type());
        entity.setIssuer(request.issuer());
        entity.setClaimsJson(writeJson(claims));
        entity.setIssuedAt(now);
        entity.setExpiresAt(toLocalDateTime(request.expiresAt()));
        entity.setParticipantId(resolveParticipant(participantId));
        credentialMapper.insert(entity);
        return toCredential(entity);
    }

    /**
     * 根据凭证 ID 查询凭证详情。
     *
     * @param id 凭证 ID。
     * @return 凭证对象。
     */
    public Credential getCredential(String id) {
        var entity = credentialMapper.selectOne(new LambdaQueryWrapper<IhCredentialEntity>()
                .eq(IhCredentialEntity::getId, id)
                .last("limit 1"));
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Credential not found: " + id);
        }
        return toCredential(entity);
    }

    /**
     * 创建标准 VP 展示对象。
     *
     * @param participantId 参与方 ID。
     * @param request 展示请求。
     * @return VP 信息。
     */
    public Map<String, Object> createPresentation(String participantId, PresentationRequest request) {
        identityBillingService.checkQuota(participantId, "IDENTITY_PRESENTATION_CREATE");
        ensureCredentialExists(request.credentialId());

        var id = "vp-" + UUID.randomUUID();
        var entity = new IhPresentationEntity();
        entity.setId(id);
        entity.setCredentialId(request.credentialId());
        entity.setHolderDid("did:web:participant-a.example.com");
        entity.setAudience(null);
        entity.setSource("IDENTITY");
        entity.setVerified(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setParticipantId(resolveParticipant(participantId));
        presentationMapper.insert(entity);

        return Map.of(
                "presentationId", id,
                "credentialId", request.credentialId(),
                "holder", entity.getHolderDid(),
                "createdAt", toInstant(entity.getCreatedAt())
        );
    }

    /**
     * 创建 DCP 场景下的展示对象。
     *
     * @param participantId 参与方 ID。
     * @param request DCP 展示请求。
     * @return DCP 展示信息。
     */
    public Map<String, Object> createDcpPresentation(String participantId, DcpPresentationRequest request) {
        identityBillingService.checkQuota(participantId, "DCP_PRESENTATION_CREATE");
        ensureCredentialExists(request.credentialId());

        var id = "dcp-vp-" + UUID.randomUUID();
        var entity = new IhPresentationEntity();
        entity.setId(id);
        entity.setCredentialId(request.credentialId());
        entity.setHolderDid("did:web:participant-a.example.com");
        entity.setAudience(request.audience());
        entity.setSource("DCP");
        entity.setVerified(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setParticipantId(resolveParticipant(participantId));
        presentationMapper.insert(entity);

        return Map.of(
                "presentationId", id,
                "credentialId", request.credentialId(),
                "audience", request.audience(),
                "issuedAt", toInstant(entity.getCreatedAt())
        );
    }

    /**
     * 校验 DCP 展示对象并更新验证状态。
     *
     * @param participantId 参与方 ID。
     * @param request DCP 校验请求。
     * @return 校验结果。
     */
    public Map<String, Object> verifyDcpPresentation(String participantId, DcpVerificationRequest request) {
        identityBillingService.checkQuota(participantId, "DCP_PRESENTATION_VERIFY");

        var entity = presentationMapper.selectOne(new LambdaQueryWrapper<IhPresentationEntity>()
                .eq(IhPresentationEntity::getId, request.presentationId())
                .last("limit 1"));
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Presentation not found: " + request.presentationId());
        }

        entity.setVerified(1);
        entity.setVerifiedAt(LocalDateTime.now());
        presentationMapper.updateById(entity);

        return Map.of(
                "presentationId", entity.getId(),
                "verified", true,
                "verifiedAt", toInstant(entity.getVerifiedAt())
        );
    }

    /**
     * 查询参与方是否具备“已签发并完成 DCP 校验”的使用资格。
     *
     * <p>资格判定规则：存在一条当前参与方的 DCP 展示记录（source=DCP）且已验证（verified=1），
     * 并且关联凭证仍在有效期内。</p>
     *
     * @param participantId 参与方 ID。
     * @param audience 目标受众（可选，传入后会按受众过滤展示记录）。
     * @return 资格检查结果，包含 qualified 与原因。
     */
    public Map<String, Object> getQualificationStatus(String participantId, String audience) {
        var participant = resolveParticipant(participantId);
        var now = LocalDateTime.now();

        var presentationQuery = new LambdaQueryWrapper<IhPresentationEntity>()
                .eq(IhPresentationEntity::getParticipantId, participant)
                .eq(IhPresentationEntity::getSource, "DCP")
                .eq(IhPresentationEntity::getVerified, 1)
                .orderByDesc(IhPresentationEntity::getVerifiedAt)
                .orderByDesc(IhPresentationEntity::getCreatedAt)
                .last("limit 1");
        if (StringUtils.hasText(audience)) {
            presentationQuery.eq(IhPresentationEntity::getAudience, audience);
        }

        var presentation = presentationMapper.selectOne(presentationQuery);
        if (presentation == null) {
            return qualificationResult(participant, audience, false, "DCP_PRESENTATION_NOT_VERIFIED", null, null, null, null, null, null);
        }

        var credential = credentialMapper.selectOne(new LambdaQueryWrapper<IhCredentialEntity>()
                .eq(IhCredentialEntity::getId, presentation.getCredentialId())
                .last("limit 1"));
        if (credential == null) {
            return qualificationResult(
                    participant,
                    audience,
                    false,
                    "CREDENTIAL_NOT_FOUND",
                    null,
                    presentation.getId(),
                    null,
                    null,
                    toInstant(presentation.getVerifiedAt()),
                    null
            );
        }

        if (!participant.equals(credential.getParticipantId())) {
            return qualificationResult(
                    participant,
                    audience,
                    false,
                    "CREDENTIAL_PARTICIPANT_MISMATCH",
                    credential.getId(),
                    presentation.getId(),
                    credential.getType(),
                    credential.getIssuer(),
                    toInstant(presentation.getVerifiedAt()),
                    toInstant(credential.getExpiresAt())
            );
        }

        var claims = readJsonMap(credential.getClaimsJson());
        var issuanceId = claims.get("issuanceId");
        if (!StringUtils.hasText(issuanceId == null ? null : String.valueOf(issuanceId))) {
            return qualificationResult(
                    participant,
                    audience,
                    false,
                    "ISSUANCE_REFERENCE_MISSING",
                    credential.getId(),
                    presentation.getId(),
                    credential.getType(),
                    credential.getIssuer(),
                    toInstant(presentation.getVerifiedAt()),
                    toInstant(credential.getExpiresAt())
            );
        }

        var issuance = queryIssuanceSafely(String.valueOf(issuanceId));
        if (issuance == null) {
            return qualificationResult(
                    participant,
                    audience,
                    false,
                    "ISSUANCE_NOT_FOUND",
                    credential.getId(),
                    presentation.getId(),
                    credential.getType(),
                    credential.getIssuer(),
                    toInstant(presentation.getVerifiedAt()),
                    toInstant(credential.getExpiresAt())
            );
        }
        var issuanceParticipant = String.valueOf(issuance.getOrDefault("participantId", ""));
        if (!participant.equals(issuanceParticipant)) {
            return qualificationResult(
                    participant,
                    audience,
                    false,
                    "ISSUANCE_PARTICIPANT_MISMATCH",
                    credential.getId(),
                    presentation.getId(),
                    credential.getType(),
                    credential.getIssuer(),
                    toInstant(presentation.getVerifiedAt()),
                    toInstant(credential.getExpiresAt())
            );
        }

        if (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(now)) {
            return qualificationResult(
                    participant,
                    audience,
                    false,
                    "CREDENTIAL_EXPIRED",
                    credential.getId(),
                    presentation.getId(),
                    credential.getType(),
                    credential.getIssuer(),
                    toInstant(presentation.getVerifiedAt()),
                    toInstant(credential.getExpiresAt())
            );
        }

        return qualificationResult(
                participant,
                audience,
                true,
                "OK",
                credential.getId(),
                presentation.getId(),
                credential.getType(),
                credential.getIssuer(),
                toInstant(presentation.getVerifiedAt()),
                toInstant(credential.getExpiresAt())
        );
    }

    private void ensureCredentialExists(String credentialId) {
        var exists = credentialMapper.selectCount(new LambdaQueryWrapper<IhCredentialEntity>()
                .eq(IhCredentialEntity::getId, credentialId));
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Credential not found: " + credentialId);
        }
    }

    private Credential toCredential(IhCredentialEntity entity) {
        return new Credential(
                entity.getId(),
                entity.getType(),
                entity.getIssuer(),
                readJsonMap(entity.getClaimsJson()),
                toInstant(entity.getIssuedAt()),
                toInstant(entity.getExpiresAt())
        );
    }

    private String resolveParticipant(String participantId) {
        return StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT;
    }

    private Map<String, Object> withIssuanceClaim(Map<String, Object> claims, String issuanceId) {
        var result = new java.util.LinkedHashMap<String, Object>();
        if (claims != null) {
            result.putAll(claims);
        }
        if (StringUtils.hasText(issuanceId)) {
            result.put("issuanceId", issuanceId);
        }
        return result;
    }

    private Map<String, Object> queryIssuanceSafely(String issuanceId) {
        try {
            return restClient.get()
                    .uri(issuerServiceBaseUrl + "/api/issuer/credentials/{issuanceId}", issuanceId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException ex) {
            return null;
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Map<String, Object> qualificationResult(
            String participantId,
            String audience,
            boolean qualified,
            String reason,
            String credentialId,
            String presentationId,
            String credentialType,
            String issuer,
            Instant verifiedAt,
            Instant expiresAt) {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("participantId", participantId);
        result.put("audience", audience);
        result.put("qualified", qualified);
        result.put("reason", reason);
        result.put("credentialId", credentialId);
        result.put("presentationId", presentationId);
        result.put("credentialType", credentialType);
        result.put("issuer", issuer);
        result.put("verifiedAt", verifiedAt);
        result.put("expiresAt", expiresAt);
        return result;
    }
}
