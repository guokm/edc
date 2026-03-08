package com.example.edc.operator.controller;

import com.example.edc.operator.dto.AuditRequest;
import com.example.edc.operator.service.OperatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final OperatorService operatorService;

    public AuditController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 创建审计事件。
     *
     * @param request 审计事件请求。
     * @return 审计记录。
     */
    @PostMapping("/events")
    public Object create(@Valid @RequestBody AuditRequest request) {
        return operatorService.createAuditRecord(request);
    }

    /**
     * 根据审计事件 ID 查询详情。
     *
     * @param id 审计事件 ID。
     * @return 审计记录。
     */
    @GetMapping("/events/{id}")
    public Object get(@PathVariable("id") String id) {
        return operatorService.getAuditRecord(id);
    }

    /**
     * 查询审计事件列表。
     *
     * @return 审计记录集合。
     */
    @GetMapping("/events")
    public Map<String, Object> list() {
        return Map.of("items", operatorService.listAuditRecords());
    }
}
