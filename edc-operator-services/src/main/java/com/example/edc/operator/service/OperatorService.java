package com.example.edc.operator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.edc.common.model.AuditRecord;
import com.example.edc.common.model.BillingRecord;
import com.example.edc.common.model.Membership;
import com.example.edc.common.model.Policy;
import com.example.edc.operator.dto.AuditRequest;
import com.example.edc.operator.dto.BillingRequest;
import com.example.edc.operator.dto.BillingUsageCheckResponse;
import com.example.edc.operator.dto.MembershipRequest;
import com.example.edc.operator.dto.PolicyRequest;
import com.example.edc.operator.entity.OpAuditEventEntity;
import com.example.edc.operator.entity.OpBillingPlanEntity;
import com.example.edc.operator.entity.OpBillingRecordEntity;
import com.example.edc.operator.entity.OpMembershipEntity;
import com.example.edc.operator.entity.OpPolicyEntity;
import com.example.edc.operator.entity.OpUsageCounterEntity;
import com.example.edc.operator.mapper.OpAuditEventMapper;
import com.example.edc.operator.mapper.OpBillingPlanMapper;
import com.example.edc.operator.mapper.OpBillingRecordMapper;
import com.example.edc.operator.mapper.OpMembershipMapper;
import com.example.edc.operator.mapper.OpPolicyMapper;
import com.example.edc.operator.mapper.OpUsageCounterMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class OperatorService {
    private static final String DEFAULT_PARTICIPANT = "participant-a";
    private static final String DEFAULT_CONSUMER_PARTICIPANT = "participant-b";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final OpMembershipMapper membershipMapper;
    private final OpPolicyMapper policyMapper;
    private final OpAuditEventMapper auditEventMapper;
    private final OpBillingRecordMapper billingRecordMapper;
    private final OpBillingPlanMapper billingPlanMapper;
    private final OpUsageCounterMapper usageCounterMapper;
    private final ObjectMapper objectMapper;

    public OperatorService(
            OpMembershipMapper membershipMapper,
            OpPolicyMapper policyMapper,
            OpAuditEventMapper auditEventMapper,
            OpBillingRecordMapper billingRecordMapper,
            OpBillingPlanMapper billingPlanMapper,
            OpUsageCounterMapper usageCounterMapper,
            ObjectMapper objectMapper) {
        this.membershipMapper = membershipMapper;
        this.policyMapper = policyMapper;
        this.auditEventMapper = auditEventMapper;
        this.billingRecordMapper = billingRecordMapper;
        this.billingPlanMapper = billingPlanMapper;
        this.usageCounterMapper = usageCounterMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建会员记录。
     *
     * @param request 会员创建请求。
     * @return 新创建的会员记录。
     */
    public Membership createMembership(MembershipRequest request) {
        var now = LocalDateTime.now();
        var entity = new OpMembershipEntity();
        entity.setId("mem-" + UUID.randomUUID());
        entity.setParticipantId(request.participantId());
        entity.setLevel(request.level());
        entity.setValidFrom(now);
        entity.setValidTo(toLocalDateTime(request.validTo()));
        entity.setStatus("ACTIVE");
        membershipMapper.insert(entity);
        return toMembership(entity);
    }

    /**
     * 根据 ID 查询会员。
     *
     * @param id 会员 ID。
     * @return 会员记录。
     */
    public Membership getMembership(String id) {
        var entity = membershipMapper.selectOne(new LambdaQueryWrapper<OpMembershipEntity>()
                .eq(OpMembershipEntity::getId, id)
                .last("limit 1"));
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Membership not found: " + id);
        }
        return toMembership(entity);
    }

    /**
     * 查询全部会员列表。
     *
     * @return 会员记录集合。
     */
    public List<Membership> listMemberships() {
        var entities = membershipMapper.selectList(new LambdaQueryWrapper<OpMembershipEntity>()
                .orderByDesc(OpMembershipEntity::getValidFrom));
        var result = new ArrayList<Membership>();
        for (var entity : entities) {
            result.add(toMembership(entity));
        }
        return result;
    }

    /**
     * 查询参与方当前生效的 ACTIVE 会员。
     *
     * @param participantId 参与方 ID。
     * @return 当前有效会员记录。
     */
    public Membership getActiveMembership(String participantId) {
        var participant = StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT;
        var active = findActiveMembership(participant, LocalDateTime.now());
        if (active == null) {
            throw new ResponseStatusException(NOT_FOUND, "Active membership not found: " + participant);
        }
        return toMembership(active);
    }

    /**
     * 创建策略记录。
     *
     * @param request 策略创建请求。
     * @return 新创建的策略记录。
     */
    public Policy createPolicy(PolicyRequest request) {
        var entity = new OpPolicyEntity();
        entity.setId("policy-" + UUID.randomUUID());
        entity.setType(request.type());
        entity.setRulesJson(writeJson(request.rules()));
        entity.setCreatedAt(LocalDateTime.now());
        policyMapper.insert(entity);
        return toPolicy(entity);
    }

    /**
     * 根据 ID 查询策略。
     *
     * @param id 策略 ID。
     * @return 策略记录。
     */
    public Policy getPolicy(String id) {
        var entity = policyMapper.selectOne(new LambdaQueryWrapper<OpPolicyEntity>()
                .eq(OpPolicyEntity::getId, id)
                .last("limit 1"));
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Policy not found: " + id);
        }
        return toPolicy(entity);
    }

    /**
     * 查询全部策略。
     *
     * @return 策略记录集合。
     */
    public List<Policy> listPolicies() {
        var entities = policyMapper.selectList(new LambdaQueryWrapper<OpPolicyEntity>()
                .orderByDesc(OpPolicyEntity::getCreatedAt));
        var result = new ArrayList<Policy>();
        for (var entity : entities) {
            result.add(toPolicy(entity));
        }
        return result;
    }

    /**
     * 创建审计事件。
     *
     * @param request 审计事件请求。
     * @return 审计记录。
     */
    public AuditRecord createAuditRecord(AuditRequest request) {
        var entity = new OpAuditEventEntity();
        entity.setId("audit-" + UUID.randomUUID());
        entity.setEventType(request.eventType());
        entity.setActorId(request.actorId());
        entity.setPayloadJson(writeJson(request.payload()));
        entity.setSignature(request.signature());
        entity.setCreatedAt(LocalDateTime.now());
        auditEventMapper.insert(entity);
        return toAuditRecord(entity);
    }

    /**
     * 根据 ID 查询审计事件。
     *
     * @param id 审计事件 ID。
     * @return 审计记录。
     */
    public AuditRecord getAuditRecord(String id) {
        var entity = auditEventMapper.selectOne(new LambdaQueryWrapper<OpAuditEventEntity>()
                .eq(OpAuditEventEntity::getId, id)
                .last("limit 1"));
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Audit record not found: " + id);
        }
        return toAuditRecord(entity);
    }

    /**
     * 查询全部审计事件。
     *
     * @return 审计记录集合。
     */
    public List<AuditRecord> listAuditRecords() {
        var entities = auditEventMapper.selectList(new LambdaQueryWrapper<OpAuditEventEntity>()
                .orderByDesc(OpAuditEventEntity::getCreatedAt));
        var result = new ArrayList<AuditRecord>();
        for (var entity : entities) {
            result.add(toAuditRecord(entity));
        }
        return result;
    }

    /**
     * 创建账单记录。
     *
     * @param request 账单创建请求。
     * @return 账单记录。
     */
    public BillingRecord createBillingRecord(BillingRequest request) {
        var entity = new OpBillingRecordEntity();
        entity.setId("bill-" + UUID.randomUUID());
        entity.setAgreementId(request.agreementId());
        entity.setPricingModel(request.pricingModel());
        entity.setAmount(BigDecimal.valueOf(request.amount()).setScale(4, RoundingMode.HALF_UP));
        entity.setCurrency(request.currency());
        entity.setPeriodStart(toLocalDateTime(request.periodStart()));
        entity.setPeriodEnd(toLocalDateTime(request.periodEnd()));
        entity.setCreatedAt(LocalDateTime.now());
        billingRecordMapper.insert(entity);
        return toBillingRecord(entity);
    }

    /**
     * 根据 ID 查询账单。
     *
     * @param id 账单 ID。
     * @return 账单记录。
     */
    public BillingRecord getBillingRecord(String id) {
        var entity = billingRecordMapper.selectOne(new LambdaQueryWrapper<OpBillingRecordEntity>()
                .eq(OpBillingRecordEntity::getId, id)
                .last("limit 1"));
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Billing record not found: " + id);
        }
        return toBillingRecord(entity);
    }

    /**
     * 查询全部账单记录。
     *
     * @return 账单记录集合。
     */
    public List<BillingRecord> listBillingRecords() {
        var entities = billingRecordMapper.selectList(new LambdaQueryWrapper<OpBillingRecordEntity>()
                .orderByDesc(OpBillingRecordEntity::getCreatedAt));
        var result = new ArrayList<BillingRecord>();
        for (var entity : entities) {
            result.add(toBillingRecord(entity));
        }
        return result;
    }

    /**
     * 按调用次数执行计费额度校验，并在通过时消耗 1 次额度。
     *
     * @param participantId 参与方 ID。
     * @param serviceCode 服务编码。
     * @return 计费校验结果。
     */
    public BillingUsageCheckResponse checkAndConsumeUsage(String participantId, String serviceCode) {
        var participant = StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT;
        var code = StringUtils.hasText(serviceCode) ? serviceCode : "GENERIC";
        var plan = findOrCreatePlan(participant, code);
        var month = YearMonth.now().format(MONTH_FORMATTER);
        var now = LocalDateTime.now();

        var usage = usageCounterMapper.selectOne(new LambdaQueryWrapper<OpUsageCounterEntity>()
                .eq(OpUsageCounterEntity::getParticipantId, participant)
                .eq(OpUsageCounterEntity::getServiceCode, code)
                .eq(OpUsageCounterEntity::getPeriodMonth, month)
                .last("limit 1"));

        if (usage == null) {
            usage = new OpUsageCounterEntity();
            usage.setId("usage-" + UUID.randomUUID());
            usage.setParticipantId(participant);
            usage.setServiceCode(code);
            usage.setPeriodMonth(month);
            usage.setUsedCount(0);
            usage.setQuotaLimit(plan.getQuotaLimit());
            usage.setUnitPrice(plan.getUnitPrice());
            usage.setLastCheckAt(now);
            usageCounterMapper.insert(usage);
        }

        var currentUsed = usage.getUsedCount() == null ? 0 : usage.getUsedCount();
        var limit = usage.getQuotaLimit() == null ? 0 : usage.getQuotaLimit();
        var allowed = currentUsed < limit;
        var nextUsed = allowed ? currentUsed + 1 : currentUsed;

        if (allowed) {
            usage.setUsedCount(nextUsed);
            usage.setLastCheckAt(now);
            usageCounterMapper.updateById(usage);
        }

        var response = new BillingUsageCheckResponse();
        response.setParticipantId(participant);
        response.setServiceCode(code);
        response.setAllowed(allowed);
        response.setUsedCount(nextUsed);
        response.setQuotaLimit(limit);
        response.setRemainingCount(Math.max(limit - nextUsed, 0));
        response.setUnitPrice(usage.getUnitPrice());
        response.setEstimatedAmount(usage.getUnitPrice().multiply(BigDecimal.valueOf(nextUsed)).setScale(4, RoundingMode.HALF_UP));
        response.setCheckedAt(now);
        return response;
    }

    /**
     * 查询按次计费的当前使用状态，不执行额度扣减。
     *
     * @param participantId 参与方 ID。
     * @param serviceCode 服务编码。
     * @return 使用状态快照。
     */
    public BillingUsageCheckResponse getUsageStatus(String participantId, String serviceCode) {
        var participant = StringUtils.hasText(participantId) ? participantId : DEFAULT_PARTICIPANT;
        var code = StringUtils.hasText(serviceCode) ? serviceCode : "GENERIC";
        var plan = findOrCreatePlan(participant, code);
        var month = YearMonth.now().format(MONTH_FORMATTER);

        var usage = usageCounterMapper.selectOne(new LambdaQueryWrapper<OpUsageCounterEntity>()
                .eq(OpUsageCounterEntity::getParticipantId, participant)
                .eq(OpUsageCounterEntity::getServiceCode, code)
                .eq(OpUsageCounterEntity::getPeriodMonth, month)
                .last("limit 1"));

        var usedCount = usage != null && usage.getUsedCount() != null ? usage.getUsedCount() : 0;
        var quotaLimit = usage != null && usage.getQuotaLimit() != null ? usage.getQuotaLimit() : plan.getQuotaLimit();
        var unitPrice = usage != null && usage.getUnitPrice() != null ? usage.getUnitPrice() : plan.getUnitPrice();
        var checkedAt = usage != null && usage.getLastCheckAt() != null ? usage.getLastCheckAt() : LocalDateTime.now();

        var response = new BillingUsageCheckResponse();
        response.setParticipantId(participant);
        response.setServiceCode(code);
        response.setAllowed(usedCount < quotaLimit);
        response.setUsedCount(usedCount);
        response.setQuotaLimit(quotaLimit);
        response.setRemainingCount(Math.max(quotaLimit - usedCount, 0));
        response.setUnitPrice(unitPrice);
        response.setEstimatedAmount(unitPrice.multiply(BigDecimal.valueOf(usedCount)).setScale(4, RoundingMode.HALF_UP));
        response.setCheckedAt(checkedAt);
        return response;
    }

    /**
     * 初始化默认计费计划，保证调用额度校验可用。
     */
    public void ensureDefaultBillingPlans() {
        var defaultCodes = List.of(
                "IDENTITY_CREDENTIAL_WRITE",
                "IDENTITY_PRESENTATION_CREATE",
                "DCP_PRESENTATION_CREATE",
                "DCP_PRESENTATION_VERIFY",
                "ISSUER_CREDENTIAL_ISSUE",
                "FEDERATED_CATALOG_QUERY",
                "FEDERATED_CRAWL_TRIGGER",
                "CONTRACT_NEGOTIATION_CREATE",
                "TRANSFER_START"
        );
        for (var code : defaultCodes) {
            findOrCreatePlan(DEFAULT_PARTICIPANT, code);
            findOrCreatePlan(DEFAULT_CONSUMER_PARTICIPANT, code);
        }
    }

    /**
     * 初始化默认会员记录，保证协商与传输阶段的会员强校验可用。
     */
    public void ensureDefaultMemberships() {
        ensureMembershipExists(DEFAULT_PARTICIPANT, "PLATINUM");
        ensureMembershipExists(DEFAULT_CONSUMER_PARTICIPANT, "GOLD");
    }

    private OpBillingPlanEntity findOrCreatePlan(String participantId, String serviceCode) {
        var entity = billingPlanMapper.selectOne(new LambdaQueryWrapper<OpBillingPlanEntity>()
                .eq(OpBillingPlanEntity::getParticipantId, participantId)
                .eq(OpBillingPlanEntity::getServiceCode, serviceCode)
                .eq(OpBillingPlanEntity::getStatus, "ACTIVE")
                .orderByDesc(OpBillingPlanEntity::getUpdatedAt)
                .last("limit 1"));

        if (entity != null) {
            return entity;
        }

        var now = LocalDateTime.now();
        var created = new OpBillingPlanEntity();
        created.setId("plan-" + UUID.randomUUID());
        created.setParticipantId(participantId);
        created.setServiceCode(serviceCode);
        created.setQuotaLimit(200);
        created.setUnitPrice(new BigDecimal("0.0500"));
        created.setStatus("ACTIVE");
        created.setUpdatedAt(now);
        billingPlanMapper.insert(created);
        return created;
    }

    private void ensureMembershipExists(String participantId, String level) {
        var now = LocalDateTime.now();
        var existing = findActiveMembership(participantId, now);
        if (existing != null) {
            return;
        }

        var entity = new OpMembershipEntity();
        entity.setId("mem-" + UUID.randomUUID());
        entity.setParticipantId(participantId);
        entity.setLevel(level);
        entity.setValidFrom(now.minusDays(1));
        entity.setValidTo(now.plusYears(5));
        entity.setStatus("ACTIVE");
        membershipMapper.insert(entity);
    }

    private OpMembershipEntity findActiveMembership(String participantId, LocalDateTime now) {
        return membershipMapper.selectOne(new LambdaQueryWrapper<OpMembershipEntity>()
                .eq(OpMembershipEntity::getParticipantId, participantId)
                .eq(OpMembershipEntity::getStatus, "ACTIVE")
                .le(OpMembershipEntity::getValidFrom, now)
                .and(wrapper -> wrapper
                        .isNull(OpMembershipEntity::getValidTo)
                        .or()
                        .ge(OpMembershipEntity::getValidTo, now))
                .orderByDesc(OpMembershipEntity::getValidFrom)
                .last("limit 1"));
    }

    private Membership toMembership(OpMembershipEntity entity) {
        return new Membership(
                entity.getId(),
                entity.getParticipantId(),
                entity.getLevel(),
                toInstant(entity.getValidFrom()),
                toInstant(entity.getValidTo()),
                entity.getStatus()
        );
    }

    private Policy toPolicy(OpPolicyEntity entity) {
        return new Policy(
                entity.getId(),
                entity.getType(),
                readJsonMap(entity.getRulesJson())
        );
    }

    private AuditRecord toAuditRecord(OpAuditEventEntity entity) {
        return new AuditRecord(
                entity.getId(),
                entity.getEventType(),
                entity.getActorId(),
                readJsonMap(entity.getPayloadJson()),
                toInstant(entity.getCreatedAt()),
                entity.getSignature()
        );
    }

    private BillingRecord toBillingRecord(OpBillingRecordEntity entity) {
        return new BillingRecord(
                entity.getId(),
                entity.getAgreementId(),
                entity.getPricingModel(),
                entity.getAmount() == null ? 0D : entity.getAmount().doubleValue(),
                entity.getCurrency(),
                toInstant(entity.getPeriodStart()),
                toInstant(entity.getPeriodEnd())
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

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private Instant toInstant(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
