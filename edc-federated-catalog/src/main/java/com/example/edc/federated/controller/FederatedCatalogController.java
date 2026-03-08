package com.example.edc.federated.controller;

import com.example.edc.federated.dto.CatalogSyncRequest;
import com.example.edc.federated.service.FederatedAccessService;
import com.example.edc.federated.service.FederatedCatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/federated")
public class FederatedCatalogController {
    private final FederatedAccessService federatedAccessService;
    private final FederatedCatalogService federatedCatalogService;

    public FederatedCatalogController(
            FederatedAccessService federatedAccessService,
            FederatedCatalogService federatedCatalogService) {
        this.federatedAccessService = federatedAccessService;
        this.federatedCatalogService = federatedCatalogService;
    }

    /**
     * 查询联邦目录聚合结果，并执行按次计费校验。
     *
     * @param participantId 参与方 ID（请求头 X-Participant-Id）。
     * @return 联邦目录条目集合。
     */
    @GetMapping("/catalog")
    public Object aggregatedCatalog(@RequestHeader(value = "X-Participant-Id", required = false) String participantId) {
        return federatedCatalogService.aggregatedCatalog(participantId);
    }

    /**
     * 触发联邦目录爬取任务，并执行按次计费校验。
     *
     * @param participantId 参与方 ID（请求头 X-Participant-Id）。
     * @return 爬取任务结果。
     */
    @PostMapping("/crawl")
    public Map<String, Object> triggerCrawl(
            @RequestHeader(value = "X-Participant-Id", required = false) String participantId) {
        return federatedCatalogService.triggerCrawl(participantId);
    }

    /**
     * 接收 Control Plane 推送的资产/Offer，同步到联邦目录。
     *
     * @param token 内部同步令牌（请求头 X-Sync-Token）。
     * @param request 目录同步请求。
     * @return 同步结果。
     */
    @PostMapping("/internal/sync")
    public Map<String, Object> syncFromControlPlane(
            @RequestHeader(value = "X-Sync-Token", required = false) String token,
            @Valid @RequestBody CatalogSyncRequest request) {
        federatedAccessService.ensureSyncPermission(token);
        return federatedCatalogService.upsertCatalogItemFromControlPlane(request);
    }
}
