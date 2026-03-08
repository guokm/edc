package com.example.edc.federated.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.edc.common.model.Asset;
import com.example.edc.common.model.CatalogEntry;
import com.example.edc.common.model.ContractOffer;
import com.example.edc.federated.dto.CatalogSyncRequest;
import com.example.edc.federated.entity.FcCatalogItemEntity;
import com.example.edc.federated.entity.FcCrawlJobEntity;
import com.example.edc.federated.mapper.FcCatalogItemMapper;
import com.example.edc.federated.mapper.FcCrawlJobMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FederatedCatalogService {
    private static final String DEFAULT_PARTICIPANT = "participant-a";

    private final FcCatalogItemMapper catalogItemMapper;
    private final FcCrawlJobMapper crawlJobMapper;
    private final FederatedBillingService federatedBillingService;
    private final String controlPlaneBaseUrl;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public FederatedCatalogService(
            FcCatalogItemMapper catalogItemMapper,
            FcCrawlJobMapper crawlJobMapper,
            FederatedBillingService federatedBillingService,
            ObjectMapper objectMapper,
            @Value("${edc.control-plane.base-url:http://localhost:8181}") String controlPlaneBaseUrl) {
        this.catalogItemMapper = catalogItemMapper;
        this.crawlJobMapper = crawlJobMapper;
        this.federatedBillingService = federatedBillingService;
        this.controlPlaneBaseUrl = controlPlaneBaseUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    /**
     * 查询联邦目录聚合结果，并执行按次计费校验。
     *
     * @param participantId 参与方 ID。
     * @return 联邦目录条目集合。
     */
    public java.util.List<CatalogEntry> aggregatedCatalog(String participantId) {
        federatedBillingService.checkQuota(participantId, "FEDERATED_CATALOG_QUERY");
        syncCatalogFromControlPlaneSafely();

        var entities = catalogItemMapper.selectList(new LambdaQueryWrapper<FcCatalogItemEntity>()
                .orderByDesc(FcCatalogItemEntity::getCreatedAt));
        var result = new ArrayList<CatalogEntry>();
        for (var entity : entities) {
            var asset = new Asset(
                    entity.getAssetId(),
                    entity.getAssetName(),
                    entity.getAssetDescription(),
                    entity.getClassification(),
                    entity.getOwnerId(),
                    readJson(entity.getMetadataJson())
            );
            var offer = new ContractOffer(
                    entity.getOfferId(),
                    entity.getAssetId(),
                    entity.getPolicyId(),
                    entity.getProviderId(),
                    entity.getCreatedAt()
            );
            result.add(new CatalogEntry(entity.getDatasetId(), asset, List.of(offer)));
        }
        return result;
    }

    /**
     * 接收 Control Plane 推送并按 Offer 维度进行目录项幂等更新。
     *
     * @param request 同步请求。
     * @return 同步结果。
     */
    public Map<String, Object> upsertCatalogItemFromControlPlane(CatalogSyncRequest request) {
        var target = catalogItemMapper.selectOne(new LambdaQueryWrapper<FcCatalogItemEntity>()
                .eq(FcCatalogItemEntity::getOfferId, request.offerId())
                .last("limit 1"));

        var entity = target == null ? new FcCatalogItemEntity() : target;
        entity.setId(target == null ? "fci-" + request.offerId() : target.getId());
        entity.setDatasetId(StringUtils.hasText(request.datasetId()) ? request.datasetId() : "dataset-" + request.assetId());
        entity.setAssetId(request.assetId());
        entity.setAssetName(request.assetName());
        entity.setAssetDescription(request.assetDescription());
        entity.setClassification(request.classification());
        entity.setOwnerId(request.ownerId());
        entity.setMetadataJson(writeJson(request.metadata()));
        entity.setOfferId(request.offerId());
        entity.setPolicyId(request.policyId());
        entity.setProviderId(request.providerId());
        entity.setCreatedAt(parseDateTime(request.createdAt()));

        if (target == null) {
            catalogItemMapper.insert(entity);
        } else {
            catalogItemMapper.updateById(entity);
        }

        return Map.of(
                "synced", true,
                "mode", target == null ? "INSERT" : "UPDATE",
                "assetId", entity.getAssetId(),
                "offerId", entity.getOfferId(),
                "catalogItemId", entity.getId()
        );
    }

    /**
     * 触发联邦目录爬取任务，并执行按次计费校验。
     *
     * @param participantId 参与方 ID。
     * @return 爬取任务结果。
     */
    public Map<String, Object> triggerCrawl(String participantId) {
        federatedBillingService.checkQuota(participantId, "FEDERATED_CRAWL_TRIGGER");

        var participant = StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT;
        var now = LocalDateTime.now();
        var crawlId = "crawl-" + UUID.randomUUID();

        var job = new FcCrawlJobEntity();
        job.setId(crawlId);
        job.setParticipantId(participant);
        job.setStatus("STARTED");
        job.setStartedAt(now);
        job.setItemCount(0);
        crawlJobMapper.insert(job);

        createCatalogItem(
                "联邦爬取资产-" + UUID.randomUUID(),
                "联邦目录爬取生成：跨域交通与运营指标资产",
                "INTERNAL",
                "华北城运",
                Map.of("行业", "智慧城市", "格式", "csv", "来源", "联邦爬取"),
                "policy-geo",
                "华北城运"
        );

        job.setStatus("FINISHED");
        job.setFinishedAt(LocalDateTime.now());
        job.setItemCount(1);
        crawlJobMapper.updateById(job);

        return Map.of(
                "crawlId", crawlId,
                "status", job.getStatus(),
                "startedAt", toInstant(job.getStartedAt()),
                "finishedAt", toInstant(job.getFinishedAt()),
                "itemCount", job.getItemCount()
        );
    }

    /**
     * 初始化默认目录数据，避免目录为空。
     */
    public void ensureSeedData() {
        var count = catalogItemMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count == 0) {
            createCatalogItem(
                    "城市交通流量聚合资产",
                    "默认联邦资产：城市路网流量、拥堵等级、路段通行效率",
                    "INTERNAL",
                    "华北城运",
                    Map.of("行业", "智慧城市", "格式", "csv", "更新频率", "15分钟"),
                    "policy-geo",
                "华北城运"
            );
        }
    }

    /**
     * 从控制面目录拉取快照并同步到联邦目录。
     */
    private void syncCatalogFromControlPlaneSafely() {
        try {
            var payload = restClient.get()
                    .uri(controlPlaneBaseUrl + "/api/catalog")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            if (payload == null || payload.isEmpty()) {
                return;
            }

            for (var item : payload) {
                var datasetId = stringValue(item.get("datasetId"));
                var asset = asMap(item.get("asset"));
                var offers = asList(item.get("offers"));
                if (asset.isEmpty() || offers.isEmpty()) {
                    continue;
                }

                var assetId = stringValue(asset.get("id"));
                var assetName = stringValue(asset.get("name"));
                var classification = stringValue(asset.get("classification"));
                var ownerId = stringValue(asset.get("ownerId"));
                if (!StringUtils.hasText(assetId) || !StringUtils.hasText(assetName) || !StringUtils.hasText(classification) || !StringUtils.hasText(ownerId)) {
                    continue;
                }

                for (var offerObj : offers) {
                    var offer = asMap(offerObj);
                    var offerId = stringValue(offer.get("id"));
                    var policyId = stringValue(offer.get("policyId"));
                    var providerId = stringValue(offer.get("providerId"));
                    if (!StringUtils.hasText(offerId) || !StringUtils.hasText(policyId) || !StringUtils.hasText(providerId)) {
                        continue;
                    }
                    upsertCatalogItemFromControlPlane(new CatalogSyncRequest(
                            StringUtils.hasText(datasetId) ? datasetId : "dataset-" + assetId,
                            assetId,
                            assetName,
                            stringValue(asset.get("description")),
                            classification,
                            ownerId,
                            asMap(asset.get("metadata")),
                            offerId,
                            policyId,
                            providerId,
                            stringValue(offer.get("createdAt"))
                    ));
                }
            }
        } catch (Exception ignored) {
            // keep federated catalog query available even if control plane is temporarily unavailable
        }
    }

    private void createCatalogItem(
            String assetName,
            String description,
            String classification,
            String ownerId,
            Map<String, Object> metadata,
            String policyId,
            String providerId) {
        var now = LocalDateTime.now();
        var item = new FcCatalogItemEntity();
        item.setId("fci-" + UUID.randomUUID());
        item.setDatasetId("dataset-" + UUID.randomUUID());
        item.setAssetId("asset-" + UUID.randomUUID());
        item.setAssetName(assetName);
        item.setAssetDescription(description);
        item.setClassification(classification);
        item.setOwnerId(ownerId);
        item.setMetadataJson(writeJson(metadata));
        item.setOfferId("offer-" + UUID.randomUUID());
        item.setPolicyId(policyId);
        item.setProviderId(providerId);
        item.setCreatedAt(now);
        catalogItemMapper.insert(item);
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private Map<String, Object> readJson(String value) {
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

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }
}
