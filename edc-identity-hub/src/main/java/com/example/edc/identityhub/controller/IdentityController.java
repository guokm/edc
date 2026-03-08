package com.example.edc.identityhub.controller;

import com.example.edc.identityhub.dto.CredentialRequest;
import com.example.edc.identityhub.dto.PresentationRequest;
import com.example.edc.identityhub.service.IdentityHubService;
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
@RequestMapping("/api/identity")
public class IdentityController {
    private final IdentityHubService identityHubService;

    public IdentityController(IdentityHubService identityHubService) {
        this.identityHubService = identityHubService;
    }

    /**
     * 查询身份中心 DID。
     *
     * @return DID 信息。
     */
    @GetMapping("/did")
    public Map<String, Object> getDid() {
        return identityHubService.getDid();
    }

    /**
     * 存储凭证并执行按次计费校验。
     *
     * @param participantId 参与方 ID（请求头 X-Participant-Id）。
     * @param request 凭证写入请求。
     * @return 凭证对象。
     */
    @PostMapping("/credentials")
    public Object storeCredential(
            @RequestHeader(value = "X-Participant-Id", required = false) String participantId,
            @Valid @RequestBody CredentialRequest request) {
        return identityHubService.storeCredential(participantId, request);
    }

    /**
     * 按凭证 ID 查询凭证详情。
     *
     * @param id 凭证 ID。
     * @return 凭证对象。
     */
    @GetMapping("/credentials/{id}")
    public Object getCredential(@PathVariable("id") String id) {
        return identityHubService.getCredential(id);
    }

    /**
     * 创建标准 VP，并执行按次计费校验。
     *
     * @param participantId 参与方 ID（请求头 X-Participant-Id）。
     * @param request VP 创建请求。
     * @return VP 信息。
     */
    @PostMapping("/presentations")
    public Map<String, Object> createPresentation(
            @RequestHeader(value = "X-Participant-Id", required = false) String participantId,
            @Valid @RequestBody PresentationRequest request) {
        return identityHubService.createPresentation(participantId, request);
    }
}
