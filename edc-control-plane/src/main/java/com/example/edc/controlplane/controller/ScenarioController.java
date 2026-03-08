package com.example.edc.controlplane.controller;

import com.example.edc.controlplane.dto.DualPlaneDemoRequest;
import com.example.edc.controlplane.dto.ScenarioRunRequest;
import com.example.edc.controlplane.service.ControlPlaneService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/scenario")
public class ScenarioController {
    private final ControlPlaneService controlPlaneService;

    public ScenarioController(ControlPlaneService controlPlaneService) {
        this.controlPlaneService = controlPlaneService;
    }

    /**
     * 运行场景化流程，自动创建测试数据并执行协商传输。
     *
     * @param request 场景执行请求，包含资产数量和消费者 ID。
     * @return 场景执行统计信息。
     */
    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody(required = false) ScenarioRunRequest request) {
        if (request == null) {
            return controlPlaneService.runScenario(3, "participant-b");
        }
        return controlPlaneService.runScenario(request.assetCount(), request.consumerId());
    }

    /**
     * 运行双数据面演示流程，强制分别经过 dp-1 与 dp-2。
     *
     * @param request 演示请求，包含消费者 ID。
     * @return 双数据面传输链路结果。
     */
    @PostMapping("/dual-plane-demo")
    public Map<String, Object> runDualPlaneDemo(@RequestBody(required = false) DualPlaneDemoRequest request) {
        if (request == null) {
            return controlPlaneService.runDualPlaneDemo("participant-b");
        }
        return controlPlaneService.runDualPlaneDemo(request.consumerId());
    }
}
