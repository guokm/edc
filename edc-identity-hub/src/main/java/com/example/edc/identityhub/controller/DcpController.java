package com.example.edc.identityhub.controller;

import com.example.edc.identityhub.dto.DcpPresentationRequest;
import com.example.edc.identityhub.dto.DcpVerificationRequest;
import com.example.edc.identityhub.service.IdentityHubService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@RestController
@RequestMapping("/api/dcp")
public class DcpController {
    private final IdentityHubService identityHubService;

    public DcpController(IdentityHubService identityHubService) {
        this.identityHubService = identityHubService;
    }

    /**
     * 创建 DCP 展示对象，并执行按次计费校验。
     *
     * @param participantId 参与方 ID（请求头 X-Participant-Id）。
     * @param request DCP 展示请求。
     * @return 展示对象信息。
     */
    @PostMapping("/presentations")
    public Map<String, Object> present(
            @RequestHeader(value = "X-Participant-Id", required = false) String participantId,
            @Valid @RequestBody DcpPresentationRequest request) {
        return identityHubService.createDcpPresentation(participantId, request);
    }

    /**
     * 校验 DCP 展示对象，并执行按次计费校验。
     *
     * @param participantId 参与方 ID（请求头 X-Participant-Id）。
     * @param request DCP 校验请求。
     * @return 校验结果。
     */
    @PostMapping("/verification")
    public Map<String, Object> verify(
            @RequestHeader(value = "X-Participant-Id", required = false) String participantId,
            @Valid @RequestBody DcpVerificationRequest request) {
        return identityHubService.verifyDcpPresentation(participantId, request);
    }

    /**
     * 查询参与方是否具备“签发+展示校验”后的可用资格。
     *
     * @param participantId 参与方 ID。
     * @param audience 目标受众（可选）。
     * @return 资格检查结果。
     */
    @GetMapping("/qualification")
    public Map<String, Object> qualification(
            @RequestParam("participantId") String participantId,
            @RequestParam(value = "audience", required = false) String audience) {
        return identityHubService.getQualificationStatus(participantId, audience);
    }
}
