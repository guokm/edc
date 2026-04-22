package com.example.edc.operator.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.edc.common.model.AuditRecord;
import com.example.edc.common.model.BillingRecord;
import com.example.edc.common.model.Membership;
import com.example.edc.common.model.Policy;
import com.example.edc.operator.dto.AuditRequest;
import com.example.edc.operator.dto.BillingRequest;
import com.example.edc.operator.dto.BillingUsageCheckResponse;
import com.example.edc.operator.dto.CurrentUserResponse;
import com.example.edc.operator.dto.LoginRequest;
import com.example.edc.operator.dto.LoginResponse;
import com.example.edc.operator.dto.MembershipRequest;
import com.example.edc.operator.dto.OrganizationRequest;
import com.example.edc.operator.dto.OrganizationView;
import com.example.edc.operator.dto.ParticipantRequest;
import com.example.edc.operator.dto.ParticipantView;
import com.example.edc.operator.dto.PolicyRequest;
import com.example.edc.operator.dto.UserAccountRequest;
import com.example.edc.operator.dto.UserAccountView;
import com.example.edc.operator.entity.OpAuditEventEntity;
import com.example.edc.operator.entity.OpBillingPlanEntity;
import com.example.edc.operator.entity.OpBillingRecordEntity;
import com.example.edc.operator.entity.OpLoginSessionEntity;
import com.example.edc.operator.entity.OpMembershipEntity;
import com.example.edc.operator.entity.OpOrganizationEntity;
import com.example.edc.operator.entity.OpParticipantEntity;
import com.example.edc.operator.entity.OpPolicyEntity;
import com.example.edc.operator.entity.OpUserAccountEntity;
import com.example.edc.operator.entity.OpUsageCounterEntity;
import com.example.edc.operator.mapper.OpAuditEventMapper;
import com.example.edc.operator.mapper.OpBillingPlanMapper;
import com.example.edc.operator.mapper.OpBillingRecordMapper;
import com.example.edc.operator.mapper.OpLoginSessionMapper;
import com.example.edc.operator.mapper.OpMembershipMapper;
import com.example.edc.operator.mapper.OpOrganizationMapper;
import com.example.edc.operator.mapper.OpParticipantMapper;
import com.example.edc.operator.mapper.OpPolicyMapper;
import com.example.edc.operator.mapper.OpUserAccountMapper;
import com.example.edc.operator.mapper.OpUsageCounterMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class OperatorService {
    private static final String DEFAULT_OPERATOR_PARTICIPANT = "operator";
    private static final String DEFAULT_PARTICIPANT = "participant-a";
    private static final String DEFAULT_CONSUMER_PARTICIPANT = "participant-b";
    private static final String DEFAULT_ACCOUNT_PASSWORD = "ChangeMe@123";
    private static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String ROLE_PROVIDER_ADMIN = "PROVIDER_ADMIN";
    private static final String ROLE_CONSUMER_ADMIN = "CONSUMER_ADMIN";
    private static final String ROLE_AUDITOR = "AUDITOR";
    private static final int SESSION_HOURS = 12;
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_LENGTH = 256;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final OpOrganizationMapper organizationMapper;
    private final OpParticipantMapper participantMapper;
    private final OpUserAccountMapper userAccountMapper;
    private final OpLoginSessionMapper loginSessionMapper;
    private final OpMembershipMapper membershipMapper;
    private final OpPolicyMapper policyMapper;
    private final OpAuditEventMapper auditEventMapper;
    private final OpBillingRecordMapper billingRecordMapper;
    private final OpBillingPlanMapper billingPlanMapper;
    private final OpUsageCounterMapper usageCounterMapper;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public OperatorService(
            OpOrganizationMapper organizationMapper,
            OpParticipantMapper participantMapper,
            OpUserAccountMapper userAccountMapper,
            OpLoginSessionMapper loginSessionMapper,
            OpMembershipMapper membershipMapper,
            OpPolicyMapper policyMapper,
            OpAuditEventMapper auditEventMapper,
            OpBillingRecordMapper billingRecordMapper,
            OpBillingPlanMapper billingPlanMapper,
            OpUsageCounterMapper usageCounterMapper,
            ObjectMapper objectMapper) {
        this.organizationMapper = organizationMapper;
        this.participantMapper = participantMapper;
        this.userAccountMapper = userAccountMapper;
        this.loginSessionMapper = loginSessionMapper;
        this.membershipMapper = membershipMapper;
        this.policyMapper = policyMapper;
        this.auditEventMapper = auditEventMapper;
        this.billingRecordMapper = billingRecordMapper;
        this.billingPlanMapper = billingPlanMapper;
        this.usageCounterMapper = usageCounterMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 使用本地运营账号登录，并生成 12 小时有效的运营会话令牌。
     *
     * @param request 登录请求，包含用户名和密码。
     * @return 登录令牌、用户视图与过期时间。
     */
    public LoginResponse login(LoginRequest request) {
        var user = userAccountMapper.selectOne(new LambdaQueryWrapper<OpUserAccountEntity>()
                .eq(OpUserAccountEntity::getUsername, request.username())
                .last("limit 1"));
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !verifyPassword(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid username or password");
        }

        var now = LocalDateTime.now();
        var session = new OpLoginSessionEntity();
        session.setToken("op-token-" + UUID.randomUUID());
        session.setUserId(user.getId());
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plusHours(SESSION_HOURS));
        session.setStatus("ACTIVE");
        loginSessionMapper.insert(session);

        createAuditEventSafely("OPERATOR_LOGIN", user, Map.of(
                "username", user.getUsername(),
                "participantId", user.getParticipantId(),
                "roleCode", user.getRoleCode(),
                "result", "SUCCESS"
        ));
        return new LoginResponse(session.getToken(), toUserAccountView(user), toInstant(session.getExpiresAt()));
    }

    /**
     * 根据运营令牌查询当前登录人。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 当前用户和令牌过期时间。
     */
    public CurrentUserResponse currentUser(String token) {
        var auth = requireAuthenticated(token);
        return new CurrentUserResponse(toUserAccountView(auth.user()), toInstant(auth.session().getExpiresAt()));
    }

    /**
     * 退出当前运营会话，并将令牌标记为已撤销。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 退出结果。
     */
    public Map<String, Object> logout(String token) {
        var auth = requireAuthenticated(token);
        auth.session().setStatus("REVOKED");
        auth.session().setLastSeenAt(LocalDateTime.now());
        loginSessionMapper.updateById(auth.session());
        createAuditEventSafely("OPERATOR_LOGOUT", auth.user(), Map.of("result", "SUCCESS"));
        return Map.of("success", true);
    }

    /**
     * 创建企业组织主数据，仅平台管理员可操作。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @param request 组织创建请求，包含企业名称、统一社会信用代码与联系人信息。
     * @return 新创建的组织视图。
     */
    public OrganizationView createOrganization(String token, OrganizationRequest request) {
        var auth = requirePlatformAdmin(token);
        if (StringUtils.hasText(request.creditCode())) {
            var duplicated = organizationMapper.selectOne(new LambdaQueryWrapper<OpOrganizationEntity>()
                    .eq(OpOrganizationEntity::getCreditCode, request.creditCode())
                    .last("limit 1"));
            if (duplicated != null) {
                throw new ResponseStatusException(CONFLICT, "Organization credit code already exists: " + request.creditCode());
            }
        }

        var now = LocalDateTime.now();
        var entity = new OpOrganizationEntity();
        entity.setId("org-" + UUID.randomUUID());
        entity.setName(request.name());
        entity.setCreditCode(request.creditCode());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setContactEmail(request.contactEmail());
        entity.setStatus(defaultText(request.status(), "ACTIVE"));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        organizationMapper.insert(entity);
        createAuditEventSafely("ORGANIZATION_CREATE", auth.user(), Map.of(
                "organizationId", entity.getId(),
                "organizationName", entity.getName(),
                "result", "SUCCESS"
        ));
        return toOrganizationView(entity);
    }

    /**
     * 查询企业组织列表，管理员和审计员可看全部，普通用户仅看自己所属组织。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 组织视图集合。
     */
    public List<OrganizationView> listOrganizations(String token) {
        var auth = requireAuthenticated(token);
        var wrapper = new LambdaQueryWrapper<OpOrganizationEntity>()
                .orderByDesc(OpOrganizationEntity::getCreatedAt);
        if (!canReadAll(auth.user())) {
            wrapper.eq(OpOrganizationEntity::getId, auth.user().getOrganizationId());
        }
        var result = new ArrayList<OrganizationView>();
        for (var entity : organizationMapper.selectList(wrapper)) {
            result.add(toOrganizationView(entity));
        }
        return result;
    }

    /**
     * 创建数据空间参与方，并绑定到某个企业组织。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @param request 参与方创建请求，包含 participantId、组织 ID 与角色类型。
     * @return 新创建的参与方视图。
     */
    public ParticipantView createParticipant(String token, ParticipantRequest request) {
        var auth = requirePlatformAdmin(token);
        assertOrganizationExists(request.organizationId());
        var existing = findParticipantByParticipantId(request.participantId());
        if (existing != null) {
            throw new ResponseStatusException(CONFLICT, "Participant already exists: " + request.participantId());
        }

        var now = LocalDateTime.now();
        var entity = new OpParticipantEntity();
        entity.setId("op-part-" + UUID.randomUUID());
        entity.setParticipantId(request.participantId());
        entity.setOrganizationId(request.organizationId());
        entity.setDisplayName(request.displayName());
        entity.setRoleType(request.roleType());
        entity.setStatus(defaultText(request.status(), "ACTIVE"));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        participantMapper.insert(entity);
        createAuditEventSafely("PARTICIPANT_CREATE", auth.user(), Map.of(
                "participantId", entity.getParticipantId(),
                "organizationId", entity.getOrganizationId(),
                "roleType", entity.getRoleType(),
                "result", "SUCCESS"
        ));
        return toParticipantView(entity);
    }

    /**
     * 查询参与方列表，管理员和审计员可看全部，普通用户仅看自己的参与方。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 参与方视图集合。
     */
    public List<ParticipantView> listParticipants(String token) {
        var auth = requireAuthenticated(token);
        var wrapper = new LambdaQueryWrapper<OpParticipantEntity>()
                .orderByDesc(OpParticipantEntity::getCreatedAt);
        if (!canReadAll(auth.user())) {
            wrapper.eq(OpParticipantEntity::getParticipantId, auth.user().getParticipantId());
        }
        var result = new ArrayList<ParticipantView>();
        for (var entity : participantMapper.selectList(wrapper)) {
            result.add(toParticipantView(entity));
        }
        return result;
    }

    /**
     * 创建运营登录账号，仅平台管理员可操作，密码使用 PBKDF2 进行落库保护。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @param request 账号创建请求，包含用户名、角色、组织、参与方与初始密码。
     * @return 新创建的账号视图，不包含密码摘要。
     */
    public UserAccountView createUserAccount(String token, UserAccountRequest request) {
        var auth = requirePlatformAdmin(token);
        assertOrganizationExists(request.organizationId());
        assertParticipantExists(request.participantId());
        var duplicated = userAccountMapper.selectOne(new LambdaQueryWrapper<OpUserAccountEntity>()
                .eq(OpUserAccountEntity::getUsername, request.username())
                .last("limit 1"));
        if (duplicated != null) {
            throw new ResponseStatusException(CONFLICT, "Username already exists: " + request.username());
        }

        var now = LocalDateTime.now();
        var entity = new OpUserAccountEntity();
        entity.setId("user-" + UUID.randomUUID());
        entity.setUsername(request.username());
        entity.setDisplayName(request.displayName());
        entity.setOrganizationId(request.organizationId());
        entity.setParticipantId(request.participantId());
        entity.setRoleCode(request.roleCode());
        entity.setPasswordHash(hashPassword(request.password()));
        entity.setStatus(defaultText(request.status(), "ACTIVE"));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userAccountMapper.insert(entity);
        createAuditEventSafely("USER_ACCOUNT_CREATE", auth.user(), Map.of(
                "userId", entity.getId(),
                "username", entity.getUsername(),
                "roleCode", entity.getRoleCode(),
                "participantId", entity.getParticipantId(),
                "result", "SUCCESS"
        ));
        return toUserAccountView(entity);
    }

    /**
     * 查询运营账号列表，管理员和审计员可看全部，普通用户仅看同参与方账号。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 账号视图集合，不包含密码摘要。
     */
    public List<UserAccountView> listUserAccounts(String token) {
        var auth = requireAuthenticated(token);
        var wrapper = new LambdaQueryWrapper<OpUserAccountEntity>()
                .orderByDesc(OpUserAccountEntity::getCreatedAt);
        if (!canReadAll(auth.user())) {
            wrapper.eq(OpUserAccountEntity::getParticipantId, auth.user().getParticipantId());
        }
        var result = new ArrayList<UserAccountView>();
        for (var entity : userAccountMapper.selectList(wrapper)) {
            result.add(toUserAccountView(entity));
        }
        return result;
    }

    /**
     * 创建会员记录。
     *
     * @param request 会员创建请求。
     * @return 新创建的会员记录。
     */
    public Membership createMembership(MembershipRequest request) {
        assertParticipantExists(request.participantId());
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

    /**
     * 初始化 V1.1 商用基线账号、组织与参与方，保证新环境可直接登录演示。
     */
    public void ensureDefaultCommercialAccounts() {
        ensureOrganizationExists("org-operator", "数据空间运营平台", "91310000OPERATOR", "平台运营", "400-000-0000", "operator@example.com");
        ensureOrganizationExists("org-provider-a", "华东车联", "91310000PROVIDERA", "车联数据负责人", "021-1000-1000", "provider@example.com");
        ensureOrganizationExists("org-consumer-b", "保险风控中心", "91310000CONSUMERB", "风控业务负责人", "021-2000-2000", "consumer@example.com");

        ensureParticipantExists("op-part-operator", DEFAULT_OPERATOR_PARTICIPANT, "org-operator", "数据空间运营平台", "OPERATOR");
        ensureParticipantExists("op-part-provider-a", DEFAULT_PARTICIPANT, "org-provider-a", "华东车联", "PROVIDER");
        ensureParticipantExists("op-part-consumer-b", DEFAULT_CONSUMER_PARTICIPANT, "org-consumer-b", "保险风控中心", "CONSUMER");

        ensureUserAccountExists("user-operator-admin", "operator_admin", "运营管理员", "org-operator",
                DEFAULT_OPERATOR_PARTICIPANT, ROLE_PLATFORM_ADMIN);
        ensureUserAccountExists("user-provider-admin", "provider_admin", "供应方管理员", "org-provider-a",
                DEFAULT_PARTICIPANT, ROLE_PROVIDER_ADMIN);
        ensureUserAccountExists("user-consumer-admin", "consumer_admin", "消费方管理员", "org-consumer-b",
                DEFAULT_CONSUMER_PARTICIPANT, ROLE_CONSUMER_ADMIN);
    }

    private void ensureOrganizationExists(
            String id,
            String name,
            String creditCode,
            String contactName,
            String contactPhone,
            String contactEmail) {
        var existing = organizationMapper.selectById(id);
        if (existing != null) {
            return;
        }

        var now = LocalDateTime.now();
        var entity = new OpOrganizationEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setCreditCode(creditCode);
        entity.setContactName(contactName);
        entity.setContactPhone(contactPhone);
        entity.setContactEmail(contactEmail);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        organizationMapper.insert(entity);
    }

    private void ensureParticipantExists(String id, String participantId, String organizationId, String displayName, String roleType) {
        var existing = findParticipantByParticipantId(participantId);
        if (existing != null) {
            return;
        }

        var now = LocalDateTime.now();
        var entity = new OpParticipantEntity();
        entity.setId(id);
        entity.setParticipantId(participantId);
        entity.setOrganizationId(organizationId);
        entity.setDisplayName(displayName);
        entity.setRoleType(roleType);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        participantMapper.insert(entity);
    }

    private void ensureUserAccountExists(
            String id,
            String username,
            String displayName,
            String organizationId,
            String participantId,
            String roleCode) {
        var existing = userAccountMapper.selectOne(new LambdaQueryWrapper<OpUserAccountEntity>()
                .eq(OpUserAccountEntity::getUsername, username)
                .last("limit 1"));
        if (existing != null) {
            return;
        }

        var now = LocalDateTime.now();
        var entity = new OpUserAccountEntity();
        entity.setId(id);
        entity.setUsername(username);
        entity.setDisplayName(displayName);
        entity.setOrganizationId(organizationId);
        entity.setParticipantId(participantId);
        entity.setRoleCode(roleCode);
        entity.setPasswordHash(hashPassword(DEFAULT_ACCOUNT_PASSWORD));
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userAccountMapper.insert(entity);
    }

    private AuthenticatedOperator requirePlatformAdmin(String token) {
        var auth = requireAuthenticated(token);
        if (!ROLE_PLATFORM_ADMIN.equals(auth.user().getRoleCode())) {
            throw new ResponseStatusException(FORBIDDEN, "Only platform admin can perform this operation");
        }
        return auth;
    }

    private AuthenticatedOperator requireAuthenticated(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing X-Operator-Token");
        }

        var now = LocalDateTime.now();
        var session = loginSessionMapper.selectOne(new LambdaQueryWrapper<OpLoginSessionEntity>()
                .eq(OpLoginSessionEntity::getToken, token)
                .eq(OpLoginSessionEntity::getStatus, "ACTIVE")
                .last("limit 1"));
        if (session == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid operator session");
        }
        if (session.getExpiresAt().isBefore(now)) {
            session.setStatus("EXPIRED");
            session.setLastSeenAt(now);
            loginSessionMapper.updateById(session);
            throw new ResponseStatusException(UNAUTHORIZED, "Operator session expired");
        }

        var user = userAccountMapper.selectById(session.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Operator user is inactive");
        }

        session.setLastSeenAt(now);
        loginSessionMapper.updateById(session);
        return new AuthenticatedOperator(user, session);
    }

    private boolean canReadAll(OpUserAccountEntity user) {
        return ROLE_PLATFORM_ADMIN.equals(user.getRoleCode()) || ROLE_AUDITOR.equals(user.getRoleCode());
    }

    private void assertOrganizationExists(String organizationId) {
        var entity = organizationMapper.selectById(organizationId);
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Organization not found: " + organizationId);
        }
    }

    private void assertParticipantExists(String participantId) {
        var entity = findParticipantByParticipantId(participantId);
        if (entity == null) {
            throw new ResponseStatusException(NOT_FOUND, "Participant not found: " + participantId);
        }
    }

    private OpParticipantEntity findParticipantByParticipantId(String participantId) {
        return participantMapper.selectOne(new LambdaQueryWrapper<OpParticipantEntity>()
                .eq(OpParticipantEntity::getParticipantId, participantId)
                .last("limit 1"));
    }

    private String hashPassword(String password) {
        var salt = new byte[16];
        secureRandom.nextBytes(salt);
        var hash = pbkdf2(password, salt, PASSWORD_ITERATIONS, PASSWORD_KEY_LENGTH);
        return "pbkdf2$" + PASSWORD_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPassword(String password, String storedHash) {
        if (!StringUtils.hasText(storedHash)) {
            return false;
        }
        var parts = storedHash.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        try {
            var iterations = Integer.parseInt(parts[1]);
            var salt = Base64.getDecoder().decode(parts[2]);
            var expected = Base64.getDecoder().decode(parts[3]);
            var actual = pbkdf2(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations, int keyLength) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    private void createAuditEventSafely(String eventType, OpUserAccountEntity actor, Map<String, Object> payload) {
        try {
            var enrichedPayload = new LinkedHashMap<String, Object>();
            if (payload != null) {
                enrichedPayload.putAll(payload);
            }
            enrichedPayload.putIfAbsent("operatorUserId", actor.getId());
            enrichedPayload.putIfAbsent("operatorUsername", actor.getUsername());
            enrichedPayload.putIfAbsent("participantId", actor.getParticipantId());
            enrichedPayload.putIfAbsent("roleCode", actor.getRoleCode());
            enrichedPayload.putIfAbsent("traceId", "trace-" + UUID.randomUUID());

            var entity = new OpAuditEventEntity();
            entity.setId("audit-" + UUID.randomUUID());
            entity.setEventType(eventType);
            entity.setActorId(actor.getId());
            entity.setPayloadJson(writeJson(enrichedPayload));
            entity.setSignature("local-demo-signature");
            entity.setCreatedAt(LocalDateTime.now());
            auditEventMapper.insert(entity);
        } catch (Exception ignored) {
            // 审计写入失败不能影响登录和基础主数据维护，后续由健康检查暴露异常。
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
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

    private OrganizationView toOrganizationView(OpOrganizationEntity entity) {
        return new OrganizationView(
                entity.getId(),
                entity.getName(),
                entity.getCreditCode(),
                entity.getContactName(),
                entity.getContactPhone(),
                entity.getContactEmail(),
                entity.getStatus(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt())
        );
    }

    private ParticipantView toParticipantView(OpParticipantEntity entity) {
        return new ParticipantView(
                entity.getId(),
                entity.getParticipantId(),
                entity.getOrganizationId(),
                entity.getDisplayName(),
                entity.getRoleType(),
                entity.getStatus(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt())
        );
    }

    private UserAccountView toUserAccountView(OpUserAccountEntity entity) {
        return new UserAccountView(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getOrganizationId(),
                entity.getParticipantId(),
                entity.getRoleCode(),
                entity.getStatus(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt())
        );
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

    private record AuthenticatedOperator(OpUserAccountEntity user, OpLoginSessionEntity session) {
    }
}
