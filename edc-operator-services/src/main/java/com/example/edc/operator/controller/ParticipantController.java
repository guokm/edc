package com.example.edc.operator.controller;

import com.example.edc.operator.dto.ParticipantRequest;
import com.example.edc.operator.service.OperatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/participants")
public class ParticipantController {
    private final OperatorService operatorService;

    public ParticipantController(OperatorService operatorService) {
        this.operatorService = operatorService;
    }

    /**
     * 创建数据空间参与方，并绑定所属企业组织。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @param request 参与方创建请求，包含 participantId、组织 ID、展示名称与角色类型。
     * @return 新创建的参与方信息。
     */
    @PostMapping
    public Object create(
            @RequestHeader(value = "X-Operator-Token", required = false) String token,
            @Valid @RequestBody ParticipantRequest request) {
        return operatorService.createParticipant(token, request);
    }

    /**
     * 查询参与方列表，按当前登录角色自动控制可见范围。
     *
     * @param token 请求头 X-Operator-Token 中传入的会话令牌。
     * @return 参与方列表。
     */
    @GetMapping
    public Map<String, Object> list(@RequestHeader(value = "X-Operator-Token", required = false) String token) {
        return Map.of("items", operatorService.listParticipants(token));
    }
}
