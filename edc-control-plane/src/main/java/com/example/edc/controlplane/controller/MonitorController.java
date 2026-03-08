package com.example.edc.controlplane.controller;

import com.example.edc.controlplane.dto.DataPlaneRuntimeResponse;
import com.example.edc.controlplane.dto.MonitorCheckResponse;
import com.example.edc.controlplane.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * 查询全模块健康状态。
     *
     * @return 健康检查结果集合。
     */
    @GetMapping("/health")
    public List<MonitorCheckResponse> health() {
        return monitorService.listHealthChecks();
    }

    /**
     * 查询治理模块接口状态。
     *
     * @return 治理接口检查结果集合。
     */
    @GetMapping("/governance")
    public List<MonitorCheckResponse> governance() {
        return monitorService.listGovernanceChecks();
    }

    /**
     * 查询双数据平面运行摘要。
     *
     * @return 数据平面运行摘要集合。
     */
    @GetMapping("/dataplanes")
    public List<DataPlaneRuntimeResponse> dataPlanes() {
        return monitorService.listDataPlaneRuntime();
    }
}
