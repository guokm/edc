package com.example.edc.issuer.service;

import com.example.edc.common.model.Credential;
import com.example.edc.issuer.dto.IssuanceRequest;
import com.example.edc.issuer.entity.IsIssuanceEntity;
import com.example.edc.issuer.mapper.IsIssuanceMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class IssuerCoreService {
    private static final String DEFAULT_PARTICIPANT = "participant-a";

    private final IsIssuanceMapper issuanceMapper;
    private final IssuerBillingService issuerBillingService;
    private final ObjectMapper objectMapper;

    public IssuerCoreService(IsIssuanceMapper issuanceMapper, IssuerBillingService issuerBillingService, ObjectMapper objectMapper) {
        this.issuanceMapper = issuanceMapper;
        this.issuerBillingService = issuerBillingService;
        this.objectMapper = objectMapper;
    }

    /**
     * 签发凭证并落库，同时执行按次计费校验。
     *
     * @param participantId 参与方 ID。
     * @param request 凭证签发请求。
     * @return 签发结果。
     */
    public Map<String, Object> issueCredential(String participantId, IssuanceRequest request) {
        issuerBillingService.checkQuota(participantId, "ISSUER_CREDENTIAL_ISSUE");

        var now = LocalDateTime.now();
        var issuanceId = "iss-" + UUID.randomUUID();
        var credentialId = "cred-" + UUID.randomUUID();

        var entity = new IsIssuanceEntity();
        entity.setIssuanceId(issuanceId);
        entity.setCredentialId(credentialId);
        entity.setType(request.type());
        entity.setIssuer(request.issuer());
        entity.setClaimsJson(writeJson(request.claims()));
        entity.setIssuedAt(now);
        entity.setExpiresAt(toLocalDateTime(request.expiresAt()));
        entity.setStatus("ISSUED");
        entity.setParticipantId(StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT);
        issuanceMapper.insert(entity);

        var credential = new Credential(
                credentialId,
                entity.getType(),
                entity.getIssuer(),
                readJsonMap(entity.getClaimsJson()),
                toInstant(entity.getIssuedAt()),
                toInstant(entity.getExpiresAt())
        );

        return Map.of(
                "issuanceId", issuanceId,
                "status", "ISSUED",
                "participantId", entity.getParticipantId(),
                "credential", credential,
                "issuedAt", toInstant(entity.getIssuedAt())
        );
    }

    /**
     * 根据签发单 ID 查询签发明细。
     *
     * @param issuanceId 签发单 ID。
     * @return 签发明细。
     */
    public Map<String, Object> getIssuance(String issuanceId) {
        var entity = issuanceMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IsIssuanceEntity>()
                .eq(IsIssuanceEntity::getIssuanceId, issuanceId)
                .last("limit 1"));
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Issuance not found: " + issuanceId);
        }
        return Map.of(
                "issuanceId", entity.getIssuanceId(),
                "credentialId", entity.getCredentialId(),
                "type", entity.getType(),
                "issuer", entity.getIssuer(),
                "claims", readJsonMap(entity.getClaimsJson()),
                "participantId", entity.getParticipantId(),
                "status", entity.getStatus(),
                "issuedAt", toInstant(entity.getIssuedAt()),
                "expiresAt", toInstant(entity.getExpiresAt())
        );
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
}
