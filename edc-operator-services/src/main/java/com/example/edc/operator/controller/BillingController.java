package com.example.edc.operator.controller;

import com.example.edc.operator.dto.BillingRequest;
import com.example.edc.operator.dto.BillingUsageCheckRequest;
import com.example.edc.operator.dto.BillingUsageCheckResponse;
import com.example.edc.operator.service.OperatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final OperatorService operatorService;

    public BillingController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 创建账单记录。
     *
     * @param request 账单创建请求。
     * @return 账单记录。
     */
    @PostMapping("/records")
    public Object create(@Valid @RequestBody BillingRequest request) {
        return operatorService.createBillingRecord(request);
    }

    /**
     * 根据账单 ID 查询详情。
     *
     * @param id 账单 ID。
     * @return 账单记录。
     */
    @GetMapping("/records/{id}")
    public Object get(@PathVariable("id") String id) {
        return operatorService.getBillingRecord(id);
    }

    /**
     * 查询账单列表。
     *
     * @return 账单记录集合。
     */
    @GetMapping("/records")
    public Map<String, Object> list() {
        return Map.of("items", operatorService.listBillingRecords());
    }

    /**
     * 按调用次数执行计费额度校验，并在通过时扣减一次额度。
     *
     * @param request 校验请求，包含参与方与服务编码。
     * @return 校验与扣减结果。
     */
    @PostMapping("/usage/check")
    public BillingUsageCheckResponse usageCheck(@Valid @RequestBody BillingUsageCheckRequest request) {
        return operatorService.checkAndConsumeUsage(request.participantId(), request.serviceCode());
    }

    /**
     * 查询按次计费当前状态，不执行扣减。
     *
     * @param participantId 参与方 ID。
     * @param serviceCode 服务编码。
     * @return 当前使用状态快照。
     */
    @GetMapping("/usage/status")
    public BillingUsageCheckResponse usageStatus(
            @RequestParam("participantId") String participantId,
            @RequestParam("serviceCode") String serviceCode) {
        return operatorService.getUsageStatus(participantId, serviceCode);
    }
}
