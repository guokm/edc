package com.example.edc.issuer.controller;

import com.example.edc.issuer.dto.IssuanceRequest;
import com.example.edc.issuer.service.IssuerCoreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/issuer")
public class IssuerController {
    private final IssuerCoreService issuerCoreService;

    public IssuerController(IssuerCoreService issuerCoreService) {
        this.issuerCoreService = issuerCoreService;
    }

    /**
     * 签发凭证并执行按次计费校验。
     *
     * @param participantId 参与方 ID（请求头 X-Participant-Id）。
     * @param request 凭证签发请求。
     * @return 签发结果。
     */
    @PostMapping("/credentials")
    public Map<String, Object> issueCredential(
            @RequestHeader(value = "X-Participant-Id", required = false) String participantId,
            @Valid @RequestBody IssuanceRequest request) {
        return issuerCoreService.issueCredential(participantId, request);
    }

    /**
     * 根据签发单 ID 查询签发明细。
     *
     * @param issuanceId 签发单 ID。
     * @return 签发明细。
     */
    @GetMapping("/credentials/{issuanceId}")
    public Map<String, Object> getIssuance(@PathVariable("issuanceId") String issuanceId) {
        return issuerCoreService.getIssuance(issuanceId);
    }
}
