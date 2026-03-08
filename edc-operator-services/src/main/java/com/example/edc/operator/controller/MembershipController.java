package com.example.edc.operator.controller;

import com.example.edc.operator.dto.MembershipRequest;
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
@RequestMapping("/api/memberships")
public class MembershipController {
    private final OperatorService operatorService;

    public MembershipController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 创建会员记录。
     *
     * @param request 会员创建请求。
     * @return 会员信息。
     */
    @PostMapping
    public Object create(@Valid @RequestBody MembershipRequest request) {
        return operatorService.createMembership(request);
    }

    /**
     * 根据会员 ID 查询详情。
     *
     * @param id 会员 ID。
     * @return 会员信息。
     */
    @GetMapping("/{id}")
    public Object get(@PathVariable("id") String id) {
        return operatorService.getMembership(id);
    }

    /**
     * 查询会员列表。
     *
     * @return 会员集合。
     */
    @GetMapping
    public Map<String, Object> list() {
        return Map.of("items", operatorService.listMemberships());
    }

    /**
     * 查询参与方当前有效的 ACTIVE 会员记录。
     *
     * @param participantId 参与方 ID。
     * @return 当前有效会员信息。
     */
    @GetMapping("/active")
    public Object active(@RequestParam("participantId") String participantId) {
        return operatorService.getActiveMembership(participantId);
    }
}
