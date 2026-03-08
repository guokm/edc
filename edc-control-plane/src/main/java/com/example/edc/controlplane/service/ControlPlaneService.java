package com.example.edc.controlplane.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.edc.common.model.Asset;
import com.example.edc.common.model.CatalogEntry;
import com.example.edc.common.model.ContractOffer;
import com.example.edc.common.model.TransferProcess;
import com.example.edc.controlplane.dto.CatalogAssetCreateRequest;
import com.example.edc.controlplane.dto.ContractNegotiationRequest;
import com.example.edc.controlplane.dto.ContractNegotiationResponse;
import com.example.edc.controlplane.dto.ContractAgreementResponse;
import com.example.edc.controlplane.dto.DataPlaneInstanceResponse;
import com.example.edc.controlplane.dto.DataPlaneRegistrationRequest;
import com.example.edc.controlplane.dto.TransferEdrResponse;
import com.example.edc.controlplane.dto.TransferOrchestrationPreviewResponse;
import com.example.edc.controlplane.dto.TransferOrchestrationStepResponse;
import com.example.edc.controlplane.dto.TransferStatusResponse;
import com.example.edc.controlplane.entity.CpDpEdrEntity;
import com.example.edc.controlplane.entity.CpDpTransferProcessEntity;
import com.example.edc.controlplane.entity.CpAssetEntity;
import com.example.edc.controlplane.entity.CpContractAgreementEntity;
import com.example.edc.controlplane.entity.CpContractNegotiationEntity;
import com.example.edc.controlplane.entity.CpContractOfferEntity;
import com.example.edc.controlplane.entity.CpDataPlaneInstanceEntity;
import com.example.edc.controlplane.entity.CpTransferProcessEntity;
import com.example.edc.controlplane.mapper.CpAssetMapper;
import com.example.edc.controlplane.mapper.CpContractAgreementMapper;
import com.example.edc.controlplane.mapper.CpContractNegotiationMapper;
import com.example.edc.controlplane.mapper.CpContractOfferMapper;
import com.example.edc.controlplane.mapper.CpDataPlaneInstanceMapper;
import com.example.edc.controlplane.mapper.CpDpEdrMapper;
import com.example.edc.controlplane.mapper.CpDpTransferProcessMapper;
import com.example.edc.controlplane.mapper.CpTransferProcessMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ControlPlaneService {
    private static final String SERVICE_CONTRACT_NEGOTIATION_CREATE = "CONTRACT_NEGOTIATION_CREATE";
    private static final String SERVICE_TRANSFER_START = "TRANSFER_START";
    private static final String BILLING_MODEL_NEGOTIATION_PER_OFFER = "NEGOTIATION_PER_OFFER";
    private static final String BILLING_MODEL_TRANSFER_PER_ASSET_CALL = "TRANSFER_PER_ASSET_CALL";

    private final CpAssetMapper assetMapper;
    private final CpContractOfferMapper offerMapper;
    private final CpContractNegotiationMapper negotiationMapper;
    private final CpContractAgreementMapper agreementMapper;
    private final CpTransferProcessMapper transferProcessMapper;
    private final CpDataPlaneInstanceMapper dataPlaneInstanceMapper;
    private final CpDpTransferProcessMapper dpTransferProcessMapper;
    private final CpDpEdrMapper dpEdrMapper;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ControlPlaneGovernanceService governanceService;
    private final String transferTopic;
    private final String federatedCatalogBaseUrl;
    private final String federatedSyncToken;
    private final RestClient restClient;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public ControlPlaneService(
            CpAssetMapper assetMapper,
            CpContractOfferMapper offerMapper,
            CpContractNegotiationMapper negotiationMapper,
            CpContractAgreementMapper agreementMapper,
            CpTransferProcessMapper transferProcessMapper,
            CpDataPlaneInstanceMapper dataPlaneInstanceMapper,
            CpDpTransferProcessMapper dpTransferProcessMapper,
            CpDpEdrMapper dpEdrMapper,
            ObjectMapper objectMapper,
            KafkaTemplate<String, String> kafkaTemplate,
            ControlPlaneGovernanceService governanceService,
            @Value("${edc.kafka.transfer-topic:edc.transfer.events}") String transferTopic,
            @Value("${edc.federated-catalog.base-url:http://localhost:8185}") String federatedCatalogBaseUrl,
            @Value("${edc.federated-catalog.sync-token:federated-sync-key}") String federatedSyncToken) {
        this.assetMapper = assetMapper;
        this.offerMapper = offerMapper;
        this.negotiationMapper = negotiationMapper;
        this.agreementMapper = agreementMapper;
        this.transferProcessMapper = transferProcessMapper;
        this.dataPlaneInstanceMapper = dataPlaneInstanceMapper;
        this.dpTransferProcessMapper = dpTransferProcessMapper;
        this.dpEdrMapper = dpEdrMapper;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.governanceService = governanceService;
        this.transferTopic = transferTopic;
        this.federatedCatalogBaseUrl = federatedCatalogBaseUrl;
        this.federatedSyncToken = federatedSyncToken;
        this.restClient = RestClient.builder().build();
    }

    /**
     * 查询目录信息，返回资产及其对应的合同 Offer。
     *
     * @return 目录条目列表。
     */
    public List<CatalogEntry> listCatalog() {
        var assets = assetMapper.selectList(new LambdaQueryWrapper<CpAssetEntity>()
                .orderByAsc(CpAssetEntity::getCreatedAt));
        var result = new ArrayList<CatalogEntry>();
        for (var assetEntity : assets) {
            var offerEntities = offerMapper.selectList(new LambdaQueryWrapper<CpContractOfferEntity>()
                    .eq(CpContractOfferEntity::getAssetId, assetEntity.getId())
                    .orderByDesc(CpContractOfferEntity::getCreatedAt));
            if (offerEntities.isEmpty()) {
                continue;
            }
            var metadata = withCreatedAt(parseMetadata(assetEntity.getMetadataJson()), assetEntity.getCreatedAt());
            var asset = new Asset(
                    assetEntity.getId(),
                    assetEntity.getName(),
                    assetEntity.getDescription(),
                    assetEntity.getClassification(),
                    assetEntity.getOwnerId(),
                    metadata
            );
            var offers = offerEntities.stream().map(this::toContractOffer).toList();
            result.add(new CatalogEntry("dataset-" + asset.id(), asset, offers));
        }
        return result;
    }

    /**
     * 按资产 ID 查询目录条目详情。
     *
     * @param assetId 资产 ID。
     * @return 单个目录条目。
     */
    public CatalogEntry getCatalogEntryByAssetId(String assetId) {
        var assetEntity = assetMapper.selectOne(new LambdaQueryWrapper<CpAssetEntity>()
                .eq(CpAssetEntity::getId, assetId)
                .last("limit 1"));
        if (assetEntity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Asset not found: " + assetId);
        }
        var offerEntities = offerMapper.selectList(new LambdaQueryWrapper<CpContractOfferEntity>()
                .eq(CpContractOfferEntity::getAssetId, assetId)
                .orderByDesc(CpContractOfferEntity::getCreatedAt));
        if (offerEntities.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Contract offer not found for asset: " + assetId);
        }
        var asset = new Asset(
                assetEntity.getId(),
                assetEntity.getName(),
                assetEntity.getDescription(),
                assetEntity.getClassification(),
                assetEntity.getOwnerId(),
                withCreatedAt(parseMetadata(assetEntity.getMetadataJson()), assetEntity.getCreatedAt())
        );
        var offers = offerEntities.stream().map(this::toContractOffer).toList();
        return new CatalogEntry("dataset-" + assetEntity.getId(), asset, offers);
    }

    /**
     * 创建目录资产并发布对应 Offer。
     *
     * @param request 资产创建请求，包含名称、描述、归属、策略及元数据。
     * @return 创建后的目录条目。
     */
    public CatalogEntry createCatalogAsset(CatalogAssetCreateRequest request) {
        var metadata = request.metadata() == null ? Map.<String, Object>of() : request.metadata();
        var created = createAssetWithOffer(
                request.name(),
                request.description(),
                request.classification(),
                request.ownerId(),
                metadata,
                request.policyId(),
                request.providerId()
        );
        publishAuditSafely(
                "ASSET_PUBLISHED",
                request.ownerId(),
                Map.of(
                        "assetId", created.assetId(),
                        "offerId", created.offerId(),
                        "policyId", request.policyId(),
                        "providerId", request.providerId()
                )
        );
        return getCatalogEntryByAssetId(created.assetId());
    }

    /**
     * 查询合同协商列表。
     *
     * @return 协商记录集合。
     */
    public List<ContractNegotiationResponse> listNegotiations() {
        var entities = negotiationMapper.selectList(new LambdaQueryWrapper<CpContractNegotiationEntity>()
                .orderByDesc(CpContractNegotiationEntity::getCreatedAt));
        var result = new ArrayList<ContractNegotiationResponse>();
        for (var entity : entities) {
            var agreement = agreementMapper.selectOne(new LambdaQueryWrapper<CpContractAgreementEntity>()
                    .eq(CpContractAgreementEntity::getNegotiationId, entity.getId())
                    .orderByDesc(CpContractAgreementEntity::getCreatedAt)
                    .last("limit 1"));
            var item = new ContractNegotiationResponse();
            item.setNegotiationId(entity.getId());
            item.setAgreementId(agreement == null ? null : agreement.getId());
            item.setAssetId(entity.getAssetId());
            item.setConsumerId(entity.getConsumerId());
            item.setOfferId(entity.getOfferId());
            item.setPolicyId(entity.getPolicyId());
            item.setState(entity.getState());
            item.setCreatedAt(entity.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * 查询合同协议列表。
     *
     * @return 协议记录集合。
     */
    public List<ContractAgreementResponse> listAgreements() {
        var entities = agreementMapper.selectList(new LambdaQueryWrapper<CpContractAgreementEntity>()
                .orderByDesc(CpContractAgreementEntity::getCreatedAt));
        var result = new ArrayList<ContractAgreementResponse>();
        for (var entity : entities) {
            var item = new ContractAgreementResponse();
            item.setAgreementId(entity.getId());
            item.setNegotiationId(entity.getNegotiationId());
            item.setAssetId(entity.getAssetId());
            item.setOfferId(entity.getOfferId());
            item.setConsumerId(entity.getConsumerId());
            item.setProviderId(entity.getProviderId());
            item.setStatus(entity.getStatus());
            item.setValidFrom(entity.getValidFrom());
            item.setValidTo(entity.getValidTo());
            item.setCreatedAt(entity.getCreatedAt());
            result.add(item);
        }
        return result;
    }

    /**
     * 发起合同协商并在 Offer 匹配时产出合同协议。
     *
     * @param request 协商请求，包含资产、消费者、Offer。
     * @return 协商结果与协议信息。
     */
    public ContractNegotiationResponse negotiateContract(ContractNegotiationRequest request) {
        governanceService.ensureActiveMembership(request.consumerId());
        governanceService.ensureCredentialQualification(request.consumerId());

        var asset = assetMapper.selectOne(new LambdaQueryWrapper<CpAssetEntity>()
                .eq(CpAssetEntity::getId, request.assetId())
                .last("limit 1"));
        if (asset == null) {
            throw new ResponseStatusException(NOT_FOUND, "Asset not found: " + request.assetId());
        }

        var offer = offerMapper.selectOne(new LambdaQueryWrapper<CpContractOfferEntity>()
                .eq(CpContractOfferEntity::getId, request.offerId())
                .last("limit 1"));
        if (offer == null) {
            var rejected = persistRejectedNegotiation(
                    request.assetId(),
                    request.consumerId(),
                    request.offerId(),
                    null,
                    "REJECTED_OFFER_NOT_FOUND"
            );
            publishEvent("NEGOTIATION_REJECTED", Map.of(
                    "negotiationId", rejected.getId(),
                    "assetId", request.assetId(),
                    "consumerId", request.consumerId(),
                    "offerId", request.offerId(),
                    "reason", "Offer not found"
            ));
            publishAuditSafely(
                    "NEGOTIATION_REJECTED",
                    request.consumerId(),
                    Map.of(
                            "negotiationId", rejected.getId(),
                            "assetId", request.assetId(),
                            "offerId", request.offerId(),
                            "reason", "REJECTED_OFFER_NOT_FOUND"
                    )
            );
            throw new ResponseStatusException(
                    CONFLICT,
                    "Offer not found: " + request.offerId() + ", negotiationId=" + rejected.getId()
            );
        }

        if (!Objects.equals(request.assetId(), offer.getAssetId())) {
            var rejected = persistRejectedNegotiation(
                    request.assetId(),
                    request.consumerId(),
                    request.offerId(),
                    offer.getPolicyId(),
                    "REJECTED_OFFER_ASSET_MISMATCH"
            );
            publishEvent("NEGOTIATION_REJECTED", Map.of(
                    "negotiationId", rejected.getId(),
                    "assetId", request.assetId(),
                    "consumerId", request.consumerId(),
                    "offerId", request.offerId(),
                    "offerAssetId", offer.getAssetId(),
                    "reason", "Offer asset mismatch"
            ));
            publishAuditSafely(
                    "NEGOTIATION_REJECTED",
                    request.consumerId(),
                    Map.of(
                            "negotiationId", rejected.getId(),
                            "assetId", request.assetId(),
                            "offerId", request.offerId(),
                            "offerAssetId", offer.getAssetId(),
                            "reason", "REJECTED_OFFER_ASSET_MISMATCH"
                    )
            );
            throw new ResponseStatusException(
                    CONFLICT,
                    "Offer " + request.offerId() + " does not belong to asset " + request.assetId()
                            + ", negotiationId=" + rejected.getId()
            );
        }

        governanceService.checkQuota(request.consumerId(), negotiationUsageCode(offer.getId()));

        var now = LocalDateTime.now();
        var negotiationId = "neg-" + UUID.randomUUID();
        var agreementId = "agr-" + UUID.randomUUID();

        var negotiation = new CpContractNegotiationEntity();
        negotiation.setId(negotiationId);
        negotiation.setAssetId(request.assetId());
        negotiation.setConsumerId(request.consumerId());
        negotiation.setOfferId(offer.getId());
        negotiation.setPolicyId(offer.getPolicyId());
        negotiation.setState("FINALIZED");
        negotiation.setCreatedAt(now);
        negotiationMapper.insert(negotiation);

        var agreement = new CpContractAgreementEntity();
        agreement.setId(agreementId);
        agreement.setNegotiationId(negotiationId);
        agreement.setAssetId(request.assetId());
        agreement.setOfferId(offer.getId());
        agreement.setConsumerId(request.consumerId());
        agreement.setProviderId(asset.getOwnerId());
        agreement.setValidFrom(now);
        agreement.setValidTo(now.plusDays(30));
        agreement.setStatus("ACTIVE");
        agreement.setCreatedAt(now);
        agreementMapper.insert(agreement);

        publishEvent("NEGOTIATION_FINALIZED", Map.of(
                "negotiationId", negotiationId,
                "agreementId", agreementId,
                "assetId", request.assetId(),
                "offerId", offer.getId(),
                "consumerId", request.consumerId()
        ));
        publishAuditSafely(
                "NEGOTIATION_FINALIZED",
                request.consumerId(),
                Map.of(
                        "negotiationId", negotiationId,
                        "agreementId", agreementId,
                        "assetId", request.assetId(),
                        "offerId", offer.getId(),
                        "policyId", offer.getPolicyId(),
                        "usageCode", negotiationUsageCode(offer.getId())
                )
        );
        createBillingRecordSafely(
                agreementId,
                BILLING_MODEL_NEGOTIATION_PER_OFFER + "|asset=" + request.assetId() + "|offer=" + offer.getId(),
                resolvePolicyUnitPrice(offer.getPolicyId()),
                "CNY",
                toInstant(now),
                toInstant(now.plusMonths(1))
        );

        var response = new ContractNegotiationResponse();
        response.setNegotiationId(negotiationId);
        response.setAgreementId(agreementId);
        response.setAssetId(request.assetId());
        response.setConsumerId(request.consumerId());
        response.setOfferId(offer.getId());
        response.setPolicyId(offer.getPolicyId());
        response.setState("FINALIZED");
        response.setCreatedAt(now);
        return response;
    }

    /**
     * 持久化失败协商记录，便于前端在协商列表中展示失败状态。
     *
     * @param assetId 资产 ID。
     * @param consumerId 消费者 ID。
     * @param offerId 请求 Offer ID。
     * @param policyId 请求策略 ID。
     * @param state 失败状态码。
     * @return 已保存的协商记录。
     */
    private CpContractNegotiationEntity persistRejectedNegotiation(
            String assetId,
            String consumerId,
            String offerId,
            String policyId,
            String state) {
        var entity = new CpContractNegotiationEntity();
        entity.setId("neg-" + UUID.randomUUID());
        entity.setAssetId(assetId);
        entity.setConsumerId(consumerId);
        entity.setOfferId(offerId);
        entity.setPolicyId(StringUtils.hasText(policyId) ? policyId : "UNKNOWN");
        entity.setState(state);
        entity.setCreatedAt(LocalDateTime.now());
        negotiationMapper.insert(entity);
        return entity;
    }

    /**
     * 启动传输流程，并调用对应 Data Plane 执行传输。
     *
     * @param agreementId 合同协议 ID。
     * @param protocol 协议名称（例如 DSP）。
     * @param requestedDataPlaneId 指定的数据面 ID，可为空（为空时自动轮询选择）。
     * @return 传输流程状态。
     */
    public TransferProcess startTransfer(String agreementId, String protocol, String requestedDataPlaneId) {
        var agreement = agreementMapper.selectOne(new LambdaQueryWrapper<CpContractAgreementEntity>()
                .eq(CpContractAgreementEntity::getId, agreementId)
                .last("limit 1"));
        if (agreement == null) {
            throw new ResponseStatusException(NOT_FOUND, "Contract agreement not found: " + agreementId);
        }
        governanceService.ensureActiveMembership(agreement.getConsumerId());
        governanceService.ensureCredentialQualification(agreement.getConsumerId());
        governanceService.checkQuota(agreement.getConsumerId(), transferUsageCode(agreement.getAssetId()));

        var dataPlane = selectDataPlane(requestedDataPlaneId);
        var now = LocalDateTime.now();
        var transferProcessId = "tp-" + UUID.randomUUID();

        var transfer = new CpTransferProcessEntity();
        transfer.setId(transferProcessId);
        transfer.setAgreementId(agreementId);
        transfer.setProtocol(protocol);
        transfer.setDataPlaneId(dataPlane.getId());
        transfer.setState("REQUESTED");
        transfer.setCreatedAt(now);
        transfer.setUpdatedAt(now);
        transferProcessMapper.insert(transfer);

        var response = restClient.post()
                .uri(dataPlane.getControlApiBaseUrl() + "/api/transfer/start")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("transferProcessId", transferProcessId))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (response == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Data plane start transfer response is empty");
        }

        var edr = asMap(response.get("edr"));
        transfer.setState("STARTED");
        transfer.setEdrEndpoint(String.valueOf(edr.getOrDefault("endpoint", "")));
        transfer.setEdrAuthToken(String.valueOf(edr.getOrDefault("authToken", "")));
        transfer.setUpdatedAt(LocalDateTime.now());
        transferProcessMapper.updateById(transfer);

        publishEvent("TRANSFER_STARTED", Map.of(
                "transferProcessId", transferProcessId,
                "agreementId", agreementId,
                "dataPlaneId", dataPlane.getId(),
                "protocol", protocol
        ));
        publishAuditSafely(
                "TRANSFER_STARTED",
                agreement.getConsumerId(),
                Map.of(
                        "transferProcessId", transferProcessId,
                        "agreementId", agreementId,
                        "assetId", agreement.getAssetId(),
                        "offerId", agreement.getOfferId(),
                        "dataPlaneId", dataPlane.getId(),
                        "usageCode", transferUsageCode(agreement.getAssetId())
                )
        );
        createBillingRecordSafely(
                agreementId,
                BILLING_MODEL_TRANSFER_PER_ASSET_CALL
                        + "|asset=" + agreement.getAssetId()
                        + "|offer=" + agreement.getOfferId()
                        + "|transfer=" + transferProcessId,
                resolvePolicyUnitPrice(resolveAgreementPolicyId(agreement.getOfferId())),
                "CNY",
                Instant.now(),
                Instant.now().plusSeconds(30L * 24 * 60 * 60)
        );

        return new TransferProcess(
                transferProcessId,
                agreementId,
                protocol,
                dataPlane.getId(),
                "STARTED",
                toInstant(now)
        );
    }

    /**
     * 预演协议传输的编排与门禁流程（不扣减计费额度）。
     *
     * <p>流程包含：协议查找、ACTIVE 会员校验、签发资格校验、计费额度只读校验、Data Plane 选择。</p>
     *
     * @param agreementId 协议 ID。
     * @param requestedDataPlaneId 指定数据面 ID，可为空。
     * @return 编排门禁预演结果。
     */
    public TransferOrchestrationPreviewResponse previewTransferOrchestration(String agreementId, String requestedDataPlaneId) {
        var agreement = agreementMapper.selectOne(new LambdaQueryWrapper<CpContractAgreementEntity>()
                .eq(CpContractAgreementEntity::getId, agreementId)
                .last("limit 1"));
        if (agreement == null) {
            throw new ResponseStatusException(NOT_FOUND, "Contract agreement not found: " + agreementId);
        }

        var steps = new ArrayList<TransferOrchestrationStepResponse>();
        var readyToTransfer = true;

        try {
            var membership = governanceService.queryActiveMembership(agreement.getConsumerId());
            var membershipId = String.valueOf(membership.getOrDefault("id", "-"));
            steps.add(buildOrchestrationStep(
                    "MEMBERSHIP_ACTIVE",
                    "ACTIVE会员校验",
                    true,
                    "会员校验通过，membershipId=" + membershipId,
                    membership
            ));
        } catch (Exception ex) {
            readyToTransfer = false;
            steps.add(buildOrchestrationStep(
                    "MEMBERSHIP_ACTIVE",
                    "ACTIVE会员校验",
                    false,
                    "会员校验失败：" + resolveFailureMessage(ex),
                    Map.of("error", resolveFailureMessage(ex))
            ));
        }

        try {
            var qualification = governanceService.queryQualificationStatus(agreement.getConsumerId(), agreement.getProviderId());
            var qualified = Boolean.TRUE.equals(qualification.get("qualified"));
            if (!qualified) {
                readyToTransfer = false;
            }
            var reason = String.valueOf(qualification.getOrDefault("reason", "UNKNOWN"));
            var detail = qualified ? "签发资格校验通过。" : "签发资格未通过，reason=" + reason;
            steps.add(buildOrchestrationStep(
                    "CREDENTIAL_QUALIFICATION",
                    "签发资格校验",
                    qualified,
                    detail,
                    qualification
            ));
        } catch (Exception ex) {
            readyToTransfer = false;
            steps.add(buildOrchestrationStep(
                    "CREDENTIAL_QUALIFICATION",
                    "签发资格校验",
                    false,
                    "签发资格校验失败：" + resolveFailureMessage(ex),
                    Map.of("error", resolveFailureMessage(ex))
            ));
        }

        try {
            var usage = governanceService.queryUsageStatus(agreement.getConsumerId(), transferUsageCode(agreement.getAssetId()));
            var allowed = Boolean.TRUE.equals(usage.get("allowed"));
            if (!allowed) {
                readyToTransfer = false;
            }
            var remaining = String.valueOf(usage.getOrDefault("remainingCount", "-"));
            var detail = allowed
                    ? "计费额度充足，remaining=" + remaining
                    : "计费额度不足，remaining=" + remaining;
            steps.add(buildOrchestrationStep(
                    "BILLING_QUOTA",
                    "按次计费校验(只读)",
                    allowed,
                    detail,
                    usage
            ));
        } catch (Exception ex) {
            readyToTransfer = false;
            steps.add(buildOrchestrationStep(
                    "BILLING_QUOTA",
                    "按次计费校验(只读)",
                    false,
                    "计费状态查询失败：" + resolveFailureMessage(ex),
                    Map.of("error", resolveFailureMessage(ex))
            ));
        }

        String selectedDataPlaneId = null;
        String selectedDataPlaneProtocol = null;
        String selectedBy = null;
        try {
            var selected = selectDataPlane(requestedDataPlaneId);
            selectedDataPlaneId = selected.getId();
            selectedDataPlaneProtocol = selected.getProtocol();
            selectedBy = StringUtils.hasText(requestedDataPlaneId) ? "REQUESTED" : "ROUND_ROBIN";
            var snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("id", selected.getId());
            snapshot.put("protocol", selected.getProtocol());
            snapshot.put("status", selected.getStatus());
            snapshot.put("publicApiBaseUrl", selected.getPublicApiBaseUrl());
            snapshot.put("controlApiBaseUrl", selected.getControlApiBaseUrl());
            snapshot.put("selectedBy", selectedBy);
            steps.add(buildOrchestrationStep(
                    "DATA_PLANE_SELECT",
                    "Data Plane 选择",
                    true,
                    "已选中数据面 " + selected.getId() + "（" + selectedBy + "）",
                    snapshot
            ));
        } catch (Exception ex) {
            readyToTransfer = false;
            steps.add(buildOrchestrationStep(
                    "DATA_PLANE_SELECT",
                    "Data Plane 选择",
                    false,
                    "数据面选择失败：" + resolveFailureMessage(ex),
                    Map.of("error", resolveFailureMessage(ex))
            ));
        }

        var suggestedTransferRequest = new LinkedHashMap<String, Object>();
        suggestedTransferRequest.put("agreementId", agreementId);
        suggestedTransferRequest.put("protocol", "DSP");
        suggestedTransferRequest.put("dataPlaneId", selectedDataPlaneId);

        var response = new TransferOrchestrationPreviewResponse();
        response.setAgreementId(agreementId);
        response.setConsumerId(agreement.getConsumerId());
        response.setProviderId(agreement.getProviderId());
        response.setRequestedDataPlaneId(requestedDataPlaneId);
        response.setSelectedDataPlaneId(selectedDataPlaneId);
        response.setSelectedDataPlaneProtocol(selectedDataPlaneProtocol);
        response.setSelectedBy(selectedBy);
        response.setReadyToTransfer(readyToTransfer);
        response.setMessage(readyToTransfer
                ? "门禁全部通过，可发起真实传输。注意：调用 POST /api/transfers 会执行按次扣费。"
                : "门禁未全部通过，请先处理失败步骤后再发起真实传输。");
        response.setSuggestedTransferRequest(suggestedTransferRequest);
        response.setSteps(steps);
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    /**
     * 根据传输流程 ID 查询 EDR 信息。
     *
     * @param transferProcessId 传输流程 ID。
     * @return EDR 响应对象。
     */
    public TransferEdrResponse resolveEdr(String transferProcessId) {
        var transfer = transferProcessMapper.selectOne(new LambdaQueryWrapper<CpTransferProcessEntity>()
                .eq(CpTransferProcessEntity::getId, transferProcessId)
                .last("limit 1"));
        if (transfer == null) {
            throw new ResponseStatusException(NOT_FOUND, "Transfer process not found: " + transferProcessId);
        }
        var response = new TransferEdrResponse();
        response.setTransferProcessId(transferProcessId);
        response.setEndpoint(transfer.getEdrEndpoint());
        response.setAuthKey("Authorization");
        response.setAuthToken(transfer.getEdrAuthToken());
        response.setUpdatedAt(transfer.getUpdatedAt());
        return response;
    }

    /**
     * 查询传输状态列表（含控制面与数据面状态）。
     *
     * @return 传输状态集合。
     */
    public List<TransferStatusResponse> listTransferStatuses() {
        var entities = transferProcessMapper.selectList(new LambdaQueryWrapper<CpTransferProcessEntity>()
                .orderByDesc(CpTransferProcessEntity::getCreatedAt));
        var result = new ArrayList<TransferStatusResponse>();
        for (var entity : entities) {
            result.add(toTransferStatus(entity));
        }
        return result;
    }

    /**
     * 查询单个传输流程的全链路轨迹。
     *
     * @param transferProcessId 传输流程 ID。
     * @return 轨迹信息，包含资产、协商、协议、传输与 EDR 明细。
     */
    public Map<String, Object> getTransferTrace(String transferProcessId) {
        var transfer = transferProcessMapper.selectOne(new LambdaQueryWrapper<CpTransferProcessEntity>()
                .eq(CpTransferProcessEntity::getId, transferProcessId)
                .last("limit 1"));
        if (transfer == null) {
            throw new ResponseStatusException(NOT_FOUND, "Transfer process not found: " + transferProcessId);
        }
        var agreement = agreementMapper.selectOne(new LambdaQueryWrapper<CpContractAgreementEntity>()
                .eq(CpContractAgreementEntity::getId, transfer.getAgreementId())
                .last("limit 1"));
        if (agreement == null) {
            throw new ResponseStatusException(NOT_FOUND, "Contract agreement not found: " + transfer.getAgreementId());
        }
        var negotiation = negotiationMapper.selectOne(new LambdaQueryWrapper<CpContractNegotiationEntity>()
                .eq(CpContractNegotiationEntity::getId, agreement.getNegotiationId())
                .last("limit 1"));
        var asset = assetMapper.selectOne(new LambdaQueryWrapper<CpAssetEntity>()
                .eq(CpAssetEntity::getId, agreement.getAssetId())
                .last("limit 1"));
        var dpTransfer = dpTransferProcessMapper.selectOne(new LambdaQueryWrapper<CpDpTransferProcessEntity>()
                .eq(CpDpTransferProcessEntity::getId, transferProcessId)
                .last("limit 1"));
        var dpEdr = dpEdrMapper.selectOne(new LambdaQueryWrapper<CpDpEdrEntity>()
                .eq(CpDpEdrEntity::getTransferProcessId, transferProcessId)
                .last("limit 1"));

        var agreementMap = new LinkedHashMap<String, Object>();
        agreementMap.put("agreementId", agreement.getId());
        agreementMap.put("negotiationId", agreement.getNegotiationId());
        agreementMap.put("assetId", agreement.getAssetId());
        agreementMap.put("offerId", agreement.getOfferId());
        agreementMap.put("consumerId", agreement.getConsumerId());
        agreementMap.put("providerId", agreement.getProviderId());
        agreementMap.put("status", agreement.getStatus());
        agreementMap.put("validFrom", agreement.getValidFrom());
        agreementMap.put("validTo", agreement.getValidTo());

        var negotiationMap = new LinkedHashMap<String, Object>();
        if (negotiation != null) {
            negotiationMap.put("negotiationId", negotiation.getId());
            negotiationMap.put("assetId", negotiation.getAssetId());
            negotiationMap.put("consumerId", negotiation.getConsumerId());
            negotiationMap.put("offerId", negotiation.getOfferId());
            negotiationMap.put("policyId", negotiation.getPolicyId());
            negotiationMap.put("state", negotiation.getState());
            negotiationMap.put("createdAt", negotiation.getCreatedAt());
        }

        var assetMap = new LinkedHashMap<String, Object>();
        if (asset != null) {
            assetMap.put("id", asset.getId());
            assetMap.put("name", asset.getName());
            assetMap.put("description", asset.getDescription());
            assetMap.put("classification", asset.getClassification());
            assetMap.put("ownerId", asset.getOwnerId());
            assetMap.put("metadata", parseMetadata(asset.getMetadataJson()));
            assetMap.put("createdAt", asset.getCreatedAt());
        }

        var dataPlaneMap = new LinkedHashMap<String, Object>();
        if (dpTransfer != null) {
            dataPlaneMap.put("id", dpTransfer.getId());
            dataPlaneMap.put("dataPlaneId", dpTransfer.getDataPlaneId());
            dataPlaneMap.put("state", dpTransfer.getState());
            dataPlaneMap.put("startedAt", dpTransfer.getStartedAt());
            dataPlaneMap.put("updatedAt", dpTransfer.getUpdatedAt());
        }

        var edrMap = new LinkedHashMap<String, Object>();
        if (dpEdr != null) {
            edrMap.put("transferProcessId", dpEdr.getTransferProcessId());
            edrMap.put("endpoint", dpEdr.getEndpoint());
            edrMap.put("authKey", dpEdr.getAuthKey());
            edrMap.put("expiresAt", dpEdr.getExpiresAt());
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("transfer", toTransferStatus(transfer));
        result.put("agreement", agreementMap);
        result.put("negotiation", negotiationMap);
        result.put("asset", assetMap);
        result.put("dataPlane", dataPlaneMap);
        result.put("edr", edrMap);
        return result;
    }

    /**
     * 注册或刷新 Data Plane 实例信息。
     *
     * @param request Data Plane 注册参数。
     * @return 注册后的 Data Plane 信息。
     */
    public DataPlaneInstanceResponse registerDataPlane(DataPlaneRegistrationRequest request) {
        var now = LocalDateTime.now();
        var existing = dataPlaneInstanceMapper.selectOne(new LambdaQueryWrapper<CpDataPlaneInstanceEntity>()
                .eq(CpDataPlaneInstanceEntity::getId, request.id())
                .last("limit 1"));

        var target = existing == null ? new CpDataPlaneInstanceEntity() : existing;
        target.setId(request.id());
        target.setPublicApiBaseUrl(request.publicApiBaseUrl());
        target.setControlApiBaseUrl(request.controlApiBaseUrl());
        target.setProtocol(StringUtils.hasText(request.protocol()) ? request.protocol() : "DSP");
        target.setStatus("ACTIVE");
        target.setLastSeenAt(now);

        if (existing == null) {
            dataPlaneInstanceMapper.insert(target);
        } else {
            dataPlaneInstanceMapper.updateById(target);
        }

        return toDataPlaneResponse(target);
    }

    /**
     * 查询所有 Data Plane 实例。
     *
     * @return Data Plane 实例列表。
     */
    public List<DataPlaneInstanceResponse> listDataPlanes() {
        var entities = dataPlaneInstanceMapper.selectList(new LambdaQueryWrapper<CpDataPlaneInstanceEntity>()
                .orderByAsc(CpDataPlaneInstanceEntity::getId));
        var result = new ArrayList<DataPlaneInstanceResponse>();
        for (var entity : entities) {
            result.add(toDataPlaneResponse(entity));
        }
        return result;
    }

    /**
     * 运行端到端场景：批量生成资产、协商、传输。
     *
     * @param assetCount 生成资产数量。
     * @param consumerId 消费方 ID。
     * @return 场景执行统计结果。
     */
    public Map<String, Object> runScenario(Integer assetCount, String consumerId) {
        var count = assetCount == null ? 3 : Math.max(assetCount, 1);
        var consumer = StringUtils.hasText(consumerId) ? consumerId : "participant-b";

        var transferIds = new ArrayList<String>();
        for (var i = 1; i <= count; i++) {
            var created = createAssetWithOffer(
                    "车辆运行数据集-批次" + i,
                    "演示用资产（第" + i + "批）：包含车辆里程、车速、油耗与驾驶行为指标",
                    "RESTRICTED",
                    "华东车联",
                    Map.of("批次", i, "行业", "智慧交通", "用途", "风控建模"),
                    "policy-basic",
                    "华东车联"
            );
            var negotiation = negotiateContract(new ContractNegotiationRequest(created.assetId(), consumer, created.offerId()));
            var transfer = startTransfer(negotiation.getAgreementId(), "DSP", null);
            transferIds.add(transfer.id());
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("assetCount", count);
        result.put("consumerId", consumer);
        result.put("transferIds", transferIds);
        result.put("rowCounts", rowCounts());
        return result;
    }

    /**
     * 演示双 Data Plane 传输流程，强制分别走 dp-1 与 dp-2。
     *
     * @param consumerId 消费方 ID。
     * @return 演示执行结果与关键链路信息。
     */
    public Map<String, Object> runDualPlaneDemo(String consumerId) {
        var consumer = StringUtils.hasText(consumerId) ? consumerId : "participant-b";
        var dataPlaneIds = List.of("dp-1", "dp-2");
        var traces = new ArrayList<Map<String, Object>>();
        var transferIds = new ArrayList<String>();

        for (var dataPlaneId : dataPlaneIds) {
            var created = createAssetWithOffer(
                    "双平面演示资产-" + dataPlaneId,
                    "演示资产将固定路由到 " + dataPlaneId + "，用于现场展示双数据平面分流能力",
                    "RESTRICTED",
                    "华东车联",
                    Map.of("演示类型", "双平面传输", "目标数据平面", dataPlaneId, "行业", "车联网"),
                    "policy-basic",
                    "华东车联"
            );
            var negotiation = negotiateContract(new ContractNegotiationRequest(created.assetId(), consumer, created.offerId()));
            var transfer = startTransfer(negotiation.getAgreementId(), "DSP", dataPlaneId);
            transferIds.add(transfer.id());
            traces.add(Map.of(
                    "dataPlaneId", dataPlaneId,
                    "assetId", created.assetId(),
                    "offerId", created.offerId(),
                    "negotiationId", negotiation.getNegotiationId(),
                    "agreementId", negotiation.getAgreementId(),
                    "transferProcessId", transfer.id()
            ));
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("runId", "dual-" + UUID.randomUUID());
        result.put("consumerId", consumer);
        result.put("transferIds", transferIds);
        result.put("steps", traces);
        result.put("transferStatuses", listTransferStatuses());
        return result;
    }

    /**
     * 执行策略匹配评估，判断 claims 是否满足 constraints。
     *
     * @param policyId 策略 ID。
     * @param claims 声明集合。
     * @param constraints 约束集合。
     * @return 评估结果。
     */
    public Map<String, Object> evaluatePolicy(String policyId, Map<String, Object> claims, Map<String, Object> constraints) {
        var safeClaims = claims == null ? Map.<String, Object>of() : claims;
        var safeConstraints = constraints == null ? Map.<String, Object>of() : constraints;
        var allowed = safeConstraints.entrySet().stream()
                .allMatch(entry -> safeClaims.containsKey(entry.getKey()) && safeClaims.get(entry.getKey()).equals(entry.getValue()));

        return Map.of(
                "policyId", policyId,
                "allowed", allowed,
                "matchedConstraints", safeConstraints.size(),
                "evaluatedAt", LocalDateTime.now().toString()
        );
    }

    /**
     * 初始化默认目录资产，避免目录为空。
     */
    public void ensureSeedData() {
        var count = assetMapper.selectCount(new LambdaQueryWrapper<CpAssetEntity>());
        if (count != null && count == 0) {
            createAssetWithOffer(
                    "城市车辆遥测主数据",
                    "默认主资产：覆盖车辆轨迹、里程、油耗与风险标签（持久化）",
                    "CONFIDENTIAL",
                    "华东车联",
                    Map.of("行业", "智慧交通", "格式", "parquet", "更新频率", "5分钟"),
                    "policy-basic",
                    "华东车联"
            );
        }
        syncAllCatalogToFederatedSafely();
    }

    private AssetOfferRef createAssetWithOffer(
            String name,
            String description,
            String classification,
            String ownerId,
            Map<String, Object> metadata,
            String policyId,
            String providerId) {
        var now = LocalDateTime.now();
        var assetId = "asset-" + UUID.randomUUID();
        var offerId = "offer-" + UUID.randomUUID();

        var asset = new CpAssetEntity();
        asset.setId(assetId);
        asset.setName(name);
        asset.setDescription(description);
        asset.setClassification(classification);
        asset.setOwnerId(ownerId);
        asset.setMetadataJson(writeJson(metadata));
        asset.setCreatedAt(now);
        assetMapper.insert(asset);

        var offer = new CpContractOfferEntity();
        offer.setId(offerId);
        offer.setAssetId(assetId);
        offer.setPolicyId(policyId);
        offer.setProviderId(providerId);
        offer.setCreatedAt(now);
        offerMapper.insert(offer);

        syncAssetToFederatedSafely(
                assetId,
                name,
                description,
                classification,
                ownerId,
                metadata,
                offerId,
                policyId,
                providerId,
                now
        );

        return new AssetOfferRef(assetId, offerId);
    }

    private CpDataPlaneInstanceEntity selectDataPlane(String requestedDataPlaneId) {
        if (StringUtils.hasText(requestedDataPlaneId)) {
            var requested = dataPlaneInstanceMapper.selectOne(new LambdaQueryWrapper<CpDataPlaneInstanceEntity>()
                    .eq(CpDataPlaneInstanceEntity::getId, requestedDataPlaneId)
                    .eq(CpDataPlaneInstanceEntity::getStatus, "ACTIVE")
                    .last("limit 1"));
            if (requested == null) {
                throw new ResponseStatusException(NOT_FOUND, "Data plane not found: " + requestedDataPlaneId);
            }
            return requested;
        }

        var actives = dataPlaneInstanceMapper.selectList(new LambdaQueryWrapper<CpDataPlaneInstanceEntity>()
                .eq(CpDataPlaneInstanceEntity::getStatus, "ACTIVE")
                .orderByAsc(CpDataPlaneInstanceEntity::getId));
        if (actives.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No active data plane registered");
        }
        var index = Math.floorMod(roundRobin.getAndIncrement(), actives.size());
        return actives.get(index);
    }

    private TransferOrchestrationStepResponse buildOrchestrationStep(
            String stepCode,
            String stepName,
            boolean passed,
            String detail,
            Map<String, Object> snapshot) {
        var step = new TransferOrchestrationStepResponse();
        step.setStepCode(stepCode);
        step.setStepName(stepName);
        step.setPassed(passed);
        step.setDetail(detail);
        step.setSnapshot(snapshot);
        return step;
    }

    private String resolveFailureMessage(Exception ex) {
        if (ex instanceof ResponseStatusException statusException) {
            return StringUtils.hasText(statusException.getReason())
                    ? statusException.getReason()
                    : statusException.getMessage();
        }
        if (ex instanceof RestClientResponseException clientResponseException) {
            var statusCode = clientResponseException.getStatusCode().value();
            var body = clientResponseException.getResponseBodyAsString();
            if (StringUtils.hasText(body)) {
                return "HTTP " + statusCode + ": " + body;
            }
            return "HTTP " + statusCode;
        }
        if (StringUtils.hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }

    /**
     * 构造协商阶段按次计费编码（按 Offer 维度统计）。
     *
     * @param offerId Offer ID。
     * @return 计费编码。
     */
    private String negotiationUsageCode(String offerId) {
        return SERVICE_CONTRACT_NEGOTIATION_CREATE + ":" + offerId;
    }

    /**
     * 构造传输阶段按次计费编码（按资产维度统计）。
     *
     * @param assetId 资产 ID。
     * @return 计费编码。
     */
    private String transferUsageCode(String assetId) {
        return SERVICE_TRANSFER_START + ":" + assetId;
    }

    /**
     * 根据 Offer 解析策略 ID。
     *
     * @param offerId Offer ID。
     * @return 策略 ID（不存在时返回 UNKNOWN）。
     */
    private String resolveAgreementPolicyId(String offerId) {
        if (!StringUtils.hasText(offerId)) {
            return "UNKNOWN";
        }
        var offer = offerMapper.selectOne(new LambdaQueryWrapper<CpContractOfferEntity>()
                .eq(CpContractOfferEntity::getId, offerId)
                .last("limit 1"));
        if (offer == null || !StringUtils.hasText(offer.getPolicyId())) {
            return "UNKNOWN";
        }
        return offer.getPolicyId();
    }

    /**
     * 依据策略 ID 解析演示用单价。
     *
     * @param policyId 策略 ID。
     * @return 单价（CNY）。
     */
    private double resolvePolicyUnitPrice(String policyId) {
        if (!StringUtils.hasText(policyId)) {
            return 0.1000D;
        }
        var normalized = policyId.toLowerCase();
        if (normalized.contains("geo")) {
            return 0.3500D;
        }
        if (normalized.contains("basic")) {
            return 0.2000D;
        }
        if (normalized.contains("premium")) {
            return 0.5000D;
        }
        return 0.1000D;
    }

    /**
     * 写入账单记录（失败不影响主流程）。
     *
     * @param agreementId 协议 ID。
     * @param pricingModel 计费模型。
     * @param amount 金额。
     * @param currency 币种。
     * @param periodStart 计费周期开始。
     * @param periodEnd 计费周期结束。
     */
    private void createBillingRecordSafely(
            String agreementId,
            String pricingModel,
            double amount,
            String currency,
            Instant periodStart,
            Instant periodEnd) {
        try {
            governanceService.createBillingRecord(
                    agreementId,
                    pricingModel,
                    amount,
                    currency,
                    periodStart,
                    periodEnd
            );
        } catch (Exception ignored) {
            // billing record is supplementary evidence, do not break core transfer/negotiation flow
        }
    }

    /**
     * 写入审计事件（失败不影响主流程）。
     *
     * @param eventType 事件类型。
     * @param actorId 事件主体。
     * @param payload 载荷。
     */
    private void publishAuditSafely(String eventType, String actorId, Map<String, Object> payload) {
        try {
            governanceService.createAuditEvent(eventType, actorId, payload, "system");
        } catch (Exception ignored) {
            // audit sink failure should not block business API availability
        }
    }

    /**
     * 将控制面资产与 Offer 同步到 Federated Catalog（失败不影响主流程）。
     *
     * @param assetId 资产 ID。
     * @param assetName 资产名称。
     * @param assetDescription 资产描述。
     * @param classification 数据分级。
     * @param ownerId 资产所有者。
     * @param metadata 资产元数据。
     * @param offerId Offer ID。
     * @param policyId 策略 ID。
     * @param providerId Provider ID。
     * @param createdAt 创建时间。
     */
    private void syncAssetToFederatedSafely(
            String assetId,
            String assetName,
            String assetDescription,
            String classification,
            String ownerId,
            Map<String, Object> metadata,
            String offerId,
            String policyId,
            String providerId,
            LocalDateTime createdAt) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("assetId", assetId);
            payload.put("assetName", assetName);
            payload.put("assetDescription", assetDescription);
            payload.put("classification", classification);
            payload.put("ownerId", ownerId);
            payload.put("metadata", metadata == null ? Map.of() : metadata);
            payload.put("offerId", offerId);
            payload.put("policyId", policyId);
            payload.put("providerId", providerId);
            payload.put("datasetId", "dataset-" + assetId);
            payload.put("createdAt", createdAt == null ? null : createdAt.toString());
            restClient.post()
                    .uri(federatedCatalogBaseUrl + "/api/federated/internal/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Sync-Token", federatedSyncToken)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // federated synchronization is best-effort to avoid coupling startup order
        }
    }

    /**
     * 全量回填控制面目录到 Federated Catalog。
     */
    private void syncAllCatalogToFederatedSafely() {
        try {
            var assets = assetMapper.selectList(new LambdaQueryWrapper<CpAssetEntity>());
            for (var asset : assets) {
                var offers = offerMapper.selectList(new LambdaQueryWrapper<CpContractOfferEntity>()
                        .eq(CpContractOfferEntity::getAssetId, asset.getId()));
                var metadata = parseMetadata(asset.getMetadataJson());
                for (var offer : offers) {
                    syncAssetToFederatedSafely(
                            asset.getId(),
                            asset.getName(),
                            asset.getDescription(),
                            asset.getClassification(),
                            asset.getOwnerId(),
                            metadata,
                            offer.getId(),
                            offer.getPolicyId(),
                            offer.getProviderId(),
                            offer.getCreatedAt()
                    );
                }
            }
        } catch (Exception ignored) {
            // keep control plane available even if federated side is unavailable
        }
    }

    private TransferStatusResponse toTransferStatus(CpTransferProcessEntity entity) {
        var dpTransfer = dpTransferProcessMapper.selectOne(new LambdaQueryWrapper<CpDpTransferProcessEntity>()
                .eq(CpDpTransferProcessEntity::getId, entity.getId())
                .last("limit 1"));
        var dpEdr = dpEdrMapper.selectOne(new LambdaQueryWrapper<CpDpEdrEntity>()
                .eq(CpDpEdrEntity::getTransferProcessId, entity.getId())
                .last("limit 1"));

        var response = new TransferStatusResponse();
        response.setTransferProcessId(entity.getId());
        response.setAgreementId(entity.getAgreementId());
        response.setDataPlaneId(entity.getDataPlaneId());
        response.setControlState(entity.getState());
        response.setDataPlaneState(dpTransfer == null ? null : dpTransfer.getState());
        response.setEdrEndpoint(entity.getEdrEndpoint());
        response.setDataPlaneEdrEndpoint(dpEdr == null ? null : dpEdr.getEndpoint());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        response.setDataPlaneUpdatedAt(dpTransfer == null ? null : dpTransfer.getUpdatedAt());
        response.setDataPlaneEdrExpiresAt(dpEdr == null ? null : dpEdr.getExpiresAt());
        return response;
    }

    private Map<String, Long> rowCounts() {
        var result = new HashMap<String, Long>();
        result.put("assets", normalizeCount(assetMapper.selectCount(new LambdaQueryWrapper<>())));
        result.put("contractOffers", normalizeCount(offerMapper.selectCount(new LambdaQueryWrapper<>())));
        result.put("contractNegotiations", normalizeCount(negotiationMapper.selectCount(new LambdaQueryWrapper<>())));
        result.put("contractAgreements", normalizeCount(agreementMapper.selectCount(new LambdaQueryWrapper<>())));
        result.put("transferProcesses", normalizeCount(transferProcessMapper.selectCount(new LambdaQueryWrapper<>())));
        result.put("dataPlaneInstances", normalizeCount(dataPlaneInstanceMapper.selectCount(new LambdaQueryWrapper<>())));
        return result;
    }

    private long normalizeCount(Long value) {
        return value == null ? 0L : value;
    }

    private record AssetOfferRef(String assetId, String offerId) {
    }

    private DataPlaneInstanceResponse toDataPlaneResponse(CpDataPlaneInstanceEntity entity) {
        var response = new DataPlaneInstanceResponse();
        response.setId(entity.getId());
        response.setPublicApiBaseUrl(entity.getPublicApiBaseUrl());
        response.setControlApiBaseUrl(entity.getControlApiBaseUrl());
        response.setProtocol(entity.getProtocol());
        response.setStatus(entity.getStatus());
        response.setLastSeenAt(entity.getLastSeenAt());
        return response;
    }

    private ContractOffer toContractOffer(CpContractOfferEntity entity) {
        return new ContractOffer(
                entity.getId(),
                entity.getAssetId(),
                entity.getPolicyId(),
                entity.getProviderId(),
                entity.getCreatedAt()
        );
    }

    private String writeJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize metadata", e);
        }
    }

    private Map<String, Object> parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse metadata", e);
        }
    }

    private Map<String, Object> withCreatedAt(Map<String, Object> metadata, LocalDateTime createdAt) {
        var merged = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.put("createdAt", createdAt == null ? null : createdAt.toString());
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private void publishEvent(String eventType, Map<String, Object> payload) {
        try {
            var message = objectMapper.writeValueAsString(Map.of(
                    "type", eventType,
                    "payload", payload,
                    "publishedAt", Instant.now().toString()
            ));
            kafkaTemplate.send(transferTopic, eventType, message);
        } catch (Exception ignored) {
            // local mode: ignore kafka publish failures
        }
    }
}
